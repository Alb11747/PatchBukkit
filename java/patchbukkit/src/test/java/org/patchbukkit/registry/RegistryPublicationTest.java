package org.patchbukkit.registry;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.patchbukkit.PatchBukkitServer;
import patchbukkit.registry.GetRegistryDataRequest;
import patchbukkit.registry.GetRegistryDataResponse;
import patchbukkit.registry.ItemDefinition;
import patchbukkit.registry.ItemRegistryData;
import patchbukkit.registry.RegistryType;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RegistryPublicationTest {
    private static final NamespacedKey STONE = NamespacedKey.minecraft("stone");
    private static final GetRegistryDataResponse ITEMS = GetRegistryDataResponse.newBuilder()
            .setItem(ItemRegistryData.newBuilder().addItems(ItemDefinition.newBuilder()
                    .setName("minecraft:stone").setMaxStackSize(64)))
            .build();

    @BeforeAll
    static void initializeServer() throws ReflectiveOperationException {
        if (Bukkit.getServer() == null) Bukkit.setServer(new PatchBukkitServer());
        NativeItemFixture.install();
    }

    @Test
    void concurrentLookupWaitsForCompletedRegistryWhileInitializerCanReenter() throws Exception {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch lookupStarted = new CountDownLatch(1);
        AtomicReference<PatchBukkitRegistry<ItemDefinition, ItemType>> reference = new AtomicReference<>();
        PatchBukkitRegistry<ItemDefinition, ItemType> registry = new PatchBukkitRegistry<>(RegistryType.ITEM,
                response -> {
                    reference.get().ensureInitialized();
                    loading.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test registry load timed out");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Test registry load interrupted", interrupted);
                    }
                    return response.getItem().getItemsList();
                }, item -> PatchBukkitItemType.create(Material.matchMaterial(item.getName()), item)) {
            @Override GetRegistryDataResponse loadRegistryData(GetRegistryDataRequest request) { return ITEMS; }
        };
        reference.set(registry);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var initializer = threads.submit(() -> registry.get(STONE));
            assertTrue(loading.await(5, TimeUnit.SECONDS));
            var reader = threads.submit(() -> {
                lookupStarted.countDown();
                return registry.get(STONE);
            });
            try {
                assertTrue(lookupStarted.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> reader.get(100, TimeUnit.MILLISECONDS),
                        "A reader must wait while item entries are still loading");
            } finally {
                release.countDown();
            }
            ItemType stone = initializer.get(5, TimeUnit.SECONDS);
            assertNotNull(stone);
            assertSame(stone, reader.get(5, TimeUnit.SECONDS));
            assertEquals(64, stone.getMaxStackSize());
        }
    }

    @Test
    void failedRegistryInitializationCanRetryWithoutPublishingPartialState() {
        AtomicInteger attempts = new AtomicInteger();
        PatchBukkitRegistry<ItemDefinition, ItemType> registry = new PatchBukkitRegistry<>(RegistryType.ITEM,
                response -> response.getItem().getItemsList(),
                item -> PatchBukkitItemType.create(Material.matchMaterial(item.getName()), item)) {
            @Override GetRegistryDataResponse loadRegistryData(GetRegistryDataRequest request) {
                if (attempts.incrementAndGet() == 1) throw new IllegalStateException("Synthetic bridge failure");
                return ITEMS;
            }
        };
        assertThrows(IllegalStateException.class, () -> registry.get(STONE));
        ItemType stone = registry.get(STONE);
        assertNotNull(stone);
        assertSame(stone, registry.get(STONE));
        assertEquals(2, attempts.get());
    }
}
