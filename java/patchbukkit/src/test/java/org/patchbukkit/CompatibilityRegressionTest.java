package org.patchbukkit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.patchbukkit.events.PatchBukkitEventManager;
import org.patchbukkit.scheduler.PatchBukkitScheduler;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class CompatibilityRegressionTest {
    @BeforeAll
    static void initializeServer() throws ReflectiveOperationException {
        if (Bukkit.getServer() == null) Bukkit.setServer(new PatchBukkitServer());
        org.patchbukkit.registry.NativeItemFixture.install();
    }

    @Test
    void materialItemPropertiesDoNotDelegateBackIntoMaterial() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertEquals(64, Material.STONE.getMaxStackSize());
            assertEquals(16, Material.ENDER_PEARL.getMaxStackSize());
            assertEquals(1, Material.DIAMOND_SWORD.getMaxStackSize());
            assertEquals(1561, Material.DIAMOND_SWORD.getMaxDurability());
            assertEquals(0, Material.STONE.getMaxDurability());
            assertTrue(Material.APPLE.isEdible());
            assertFalse(Material.STONE.isEdible());
            assertTrue(Material.MUSIC_DISC_13.isRecord());
            assertFalse(Material.STONE.isRecord());
            assertNull(Material.WATER.asItemType());
        });
    }

    private static final class TestEvent extends Event {
        private final HandlerList handlers = new HandlerList();
        TestEvent(boolean async) { super(async); }
        @Override public HandlerList getHandlers() { return handlers; }
    }

    @Test
    void eventValidationUsesActualDispatchThreadIdentity() throws Exception {
        PatchBukkitServer server = new PatchBukkitServer();
        PatchBukkitEventManager events = new PatchBukkitEventManager(server);
        assertTrue(server.isPrimaryThread());
        assertDoesNotThrow(() -> events.callEvent(new TestEvent(false)));
        assertThrows(IllegalStateException.class, () -> events.callEvent(new TestEvent(true)));
        try (var background = Executors.newSingleThreadExecutor()) {
            background.submit(() -> {
                assertFalse(server.isPrimaryThread());
                assertDoesNotThrow(() -> events.callEvent(new TestEvent(true)));
                assertThrows(IllegalStateException.class, () -> events.callEvent(new TestEvent(false)));
            }).get(5, TimeUnit.SECONDS);
        }
    }

    private static Plugin plugin() {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLogger" -> Logger.getLogger("scheduler-regression");
                    case "isEnabled" -> true;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> null;
                });
    }

    @Test
    void syncTasksAndCallSyncMethodRunOnDispatchThreadWhileAsyncTasksDoNot() throws Exception {
        PatchBukkitScheduler scheduler = new PatchBukkitScheduler();
        Plugin plugin = plugin();
        Thread dispatch = Thread.currentThread();
        CompletableFuture<Thread> sync = new CompletableFuture<>();
        CompletableFuture<Thread> async = new CompletableFuture<>();
        CompletableFuture<Thread> mainExecutor = new CompletableFuture<>();
        BukkitTask syncTask = scheduler.runTask(plugin, () -> sync.complete(Thread.currentThread()));
        BukkitTask asyncTask = scheduler.runTaskAsynchronously(plugin, () -> async.complete(Thread.currentThread()));
        var callable = scheduler.callSyncMethod(plugin, Thread::currentThread);
        scheduler.getMainThreadExecutor(plugin).execute(() -> mainExecutor.complete(Thread.currentThread()));
        assertTrue(syncTask.isSync());
        assertFalse(asyncTask.isSync());
        assertFalse(sync.isDone());
        assertFalse(callable.isDone());
        assertFalse(mainExecutor.isDone());
        scheduler.tick();
        assertSame(dispatch, sync.get(5, TimeUnit.SECONDS));
        assertSame(dispatch, callable.get(5, TimeUnit.SECONDS));
        assertSame(dispatch, mainExecutor.get(5, TimeUnit.SECONDS));
        assertNotSame(dispatch, async.get(5, TimeUnit.SECONDS));
        assertFalse(syncTask.isCancelled(), "Completed tasks are not cancelled tasks");
    }

    @Test
    void delayedRepeatingSyncTaskCanCancelItselfWithRealConsumerHandle() {
        PatchBukkitScheduler scheduler = new PatchBukkitScheduler();
        int[] calls = {0};
        scheduler.runTaskTimer(plugin(), task -> {
            assertNotNull(task);
            assertTrue(task.isSync());
            if (++calls[0] == 2) task.cancel();
        }, 2, 2);
        scheduler.tick();
        assertEquals(0, calls[0]);
        scheduler.tick();
        assertEquals(1, calls[0]);
        scheduler.tick();
        assertEquals(1, calls[0]);
        scheduler.tick();
        assertEquals(2, calls[0]);
        scheduler.tick();
        scheduler.tick();
        assertEquals(2, calls[0]);
    }

    @Test
    void pluginTaskCancellationReleasesBackgroundSyncCallableWaiter() throws Exception {
        PatchBukkitScheduler scheduler = new PatchBukkitScheduler();
        Plugin plugin = plugin();
        AtomicInteger calls = new AtomicInteger();
        var result = scheduler.callSyncMethod(plugin, calls::incrementAndGet);
        CountDownLatch waiting = new CountDownLatch(1);
        try (var background = Executors.newSingleThreadExecutor()) {
            var waiter = background.submit(() -> {
                waiting.countDown();
                assertThrows(CancellationException.class, () -> result.get(2, TimeUnit.SECONDS));
            });
            assertTrue(waiting.await(2, TimeUnit.SECONDS));
            scheduler.cancelTasks(plugin);
            waiter.get(5, TimeUnit.SECONDS);
        }
        assertTrue(result.isDone());
        assertTrue(result.isCancelled());
        scheduler.tick();
        assertEquals(0, calls.get());
    }

    @Test
    void syncCallableCancellationWorksThroughTaskIdAndReturnedFuture() throws Exception {
        PatchBukkitScheduler scheduler = new PatchBukkitScheduler();
        Plugin plugin = plugin();
        AtomicInteger calls = new AtomicInteger();
        var byTask = scheduler.callSyncMethod(plugin, calls::incrementAndGet);
        var pending = scheduler.getPendingTasks();
        assertEquals(1, pending.size());
        scheduler.cancelTask(pending.getFirst().getTaskId());
        assertTrue(byTask.isDone());
        assertTrue(byTask.isCancelled());
        assertThrows(CancellationException.class, () -> byTask.get(2, TimeUnit.SECONDS));

        var byFuture = scheduler.callSyncMethod(plugin, calls::incrementAndGet);
        var otherPlugin = scheduler.callSyncMethod(plugin(), () -> 42);
        assertTrue(byFuture.cancel(false));
        assertTrue(byFuture.isDone());
        assertTrue(byFuture.isCancelled());
        assertTrue(scheduler.getPendingTasks().stream().noneMatch(task -> task.getOwner() == plugin));
        assertFalse(otherPlugin.isDone());
        scheduler.tick();
        assertEquals(42, otherPlugin.get(2, TimeUnit.SECONDS));
        assertEquals(0, calls.get());
    }
}
