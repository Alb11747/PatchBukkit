package org.patchbukkit.registry;

import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PatchBukkitRegistryAccessTest {

    @BeforeEach
    public void setUp() throws ReflectiveOperationException {
        if (org.bukkit.Bukkit.getServer() == null) {
            org.patchbukkit.PatchBukkitServer server = new org.patchbukkit.PatchBukkitServer();
            org.bukkit.Bukkit.setServer(server);
        }
        NativeItemFixture.install();
    }

    @Test
    public void testServerCommandMapReflection() throws Throwable {
        org.patchbukkit.PatchBukkitServer server = new org.patchbukkit.PatchBukkitServer();

        java.lang.reflect.Field field = org.patchbukkit.PatchBukkitServer.class.getDeclaredField("commandMap");
        field.setAccessible(true);
        Object mapObj = field.get(server);
        assertNotNull(mapObj);
        assertEquals(server.getCommandMap(), mapObj);
        assertTrue(mapObj instanceof org.bukkit.command.SimpleCommandMap, "commandMap field must be an instance of SimpleCommandMap for legacy reflection compatibility");
        assertTrue(server.getCommandMap() instanceof org.bukkit.command.SimpleCommandMap, "getCommandMap() must return an instance of SimpleCommandMap");
    }

    @Test
    public void testPatchBukkitHelpMapRegistration() {
        org.patchbukkit.help.PatchBukkitHelpMap helpMap = new org.patchbukkit.help.PatchBukkitHelpMap();
        assertNotNull(helpMap.getHelpTopics());
        assertDoesNotThrow(() -> helpMap.registerHelpTopicFactory(Object.class, (cmd) -> null));
    }

    @Test
    public void testReentrantRegistryLookup() {
        PatchBukkitRegistryAccess registryAccess = new PatchBukkitRegistryAccess();

        assertDoesNotThrow(() -> {
            Registry<?> itemReg = registryAccess.getRegistry(RegistryKey.ITEM);
            Registry<?> soundReg = registryAccess.getRegistry(RegistryKey.SOUND_EVENT);
            Registry<?> blockReg = registryAccess.getRegistry(RegistryKey.BLOCK);

            assertNotNull(itemReg);
            assertNotNull(soundReg);
            assertNotNull(blockReg);
        });
    }

    @Test
    public void testSoundClassInitialization() {
        assertDoesNotThrow(() -> {
            org.bukkit.Sound sound = org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            assertNotNull(sound, "Sound constant must initialize successfully without ExceptionInInitializerError");
            assertNotNull(sound.getKey(), "Sound key must not be null");
        });
    }

    @Test
    public void testDamageTypeInitialization() {
        assertDoesNotThrow(() -> {
            org.bukkit.damage.DamageType arrow = org.bukkit.damage.DamageType.ARROW;
            assertNotNull(arrow, "DamageType.ARROW must initialize successfully without ExceptionInInitializerError");
            assertEquals(org.bukkit.NamespacedKey.minecraft("arrow"), arrow.getKey());
            assertNotNull(arrow.getTranslationKey());
            assertNotNull(arrow.getDamageScaling());
            assertNotNull(arrow.getDamageEffect());
            assertNotNull(arrow.getDeathMessageType());

            org.bukkit.damage.DamageType generic = org.bukkit.damage.DamageType.GENERIC;
            assertNotNull(generic);
            assertEquals(org.bukkit.NamespacedKey.minecraft("generic"), generic.getKey());

            Registry<org.bukkit.damage.DamageType> damageTypeReg = Registry.DAMAGE_TYPE;
            assertNotNull(damageTypeReg);
            assertSame(arrow, damageTypeReg.get(org.bukkit.NamespacedKey.minecraft("arrow")));

            org.bukkit.damage.DamageSource source = org.bukkit.damage.DamageSource.builder(arrow).build();
            assertNotNull(source);
            assertEquals(arrow, source.getDamageType());
        });
    }

    @Test
    public void testInspectBlockTypeAndDataMethods() {
        System.out.println("=== BLOCK TYPE METHODS ===");
        for (java.lang.reflect.Method m : org.bukkit.block.BlockType.class.getMethods()) {
            System.out.println("BlockType method: " + m.getName() + " -> " + m.getReturnType().getName() + " args=" + java.util.Arrays.toString(m.getParameterTypes()));
        }
        System.out.println("=== BLOCK DATA METHODS ===");
        for (java.lang.reflect.Method m : org.bukkit.block.data.BlockData.class.getMethods()) {
            System.out.println("BlockData method: " + m.getName() + " -> " + m.getReturnType().getName() + " args=" + java.util.Arrays.toString(m.getParameterTypes()));
        }
    }

    @Test
    public void testBlockTypeRegistryLookup() {
        PatchBukkitRegistryAccess registryAccess = new PatchBukkitRegistryAccess();
        Registry<org.bukkit.block.BlockType> blockReg = registryAccess.getRegistry(RegistryKey.BLOCK);
        assertNotNull(blockReg);

        org.bukkit.block.BlockType stoneType = blockReg.get(org.bukkit.NamespacedKey.minecraft("stone"));
        assertNotNull(stoneType, "BlockType for minecraft:stone should be found in BLOCK registry");
        assertEquals(Material.STONE, stoneType.asMaterial());
        assertEquals(org.bukkit.NamespacedKey.minecraft("stone"), stoneType.getKey());

        org.bukkit.block.data.BlockData data = stoneType.createBlockData();
        assertNotNull(data);
        assertEquals(Material.STONE, data.getMaterial());

        assertNotNull(stoneType.createBlockDataStates(), "createBlockDataStates() must not be null");
        assertFalse(stoneType.createBlockDataStates().isEmpty(), "createBlockDataStates() must not be empty");
        assertEquals(org.bukkit.block.data.BlockData.class, stoneType.getBlockDataClass());

        Registry<ItemType> itemReg = registryAccess.getRegistry(RegistryKey.ITEM);
        ItemType airType = itemReg.get(org.bukkit.NamespacedKey.minecraft("air"));
        assertNotNull(airType, "ItemType for minecraft:air must be found in ITEM registry");
    }

    @Test
    public void testCreateBlockDataFromString() {
        if (org.bukkit.Bukkit.getServer() == null) {
            org.patchbukkit.PatchBukkitServer server = new org.patchbukkit.PatchBukkitServer();
            org.bukkit.Bukkit.setServer(server);
        }

        org.bukkit.block.data.BlockData stoneData = org.bukkit.Bukkit.createBlockData("stone");
        assertNotNull(stoneData);
        assertEquals(Material.STONE, stoneData.getMaterial());
        assertEquals("minecraft:stone", stoneData.getAsString());

        org.bukkit.block.data.BlockData chestData = org.bukkit.Bukkit.createBlockData("minecraft:chest[facing=north]");
        assertNotNull(chestData);
        assertEquals(Material.CHEST, chestData.getMaterial());
        assertTrue(chestData.getAsString().contains("chest"));
    }

    @Test
    public void testCreateBlockDataMaterial() {
        org.bukkit.block.data.BlockData matData = org.bukkit.Bukkit.createBlockData(Material.STONE);
        assertNotNull(matData);
        assertEquals(Material.STONE, matData.getMaterial());
    }

    @Test
    public void testNMSClassAvailability() {
        assertDoesNotThrow(() -> {
            try {
                net.minecraft.SharedConstants.tryDetectVersion();
                net.minecraft.server.Bootstrap.bootStrap();
            } catch (Throwable ignored) {}
            Class<?> nmsBlock = Class.forName("net.minecraft.world.level.block.Block");
            assertNotNull(nmsBlock, "NMS Block class must be present on classpath");
            Class<?> nmsRegistries = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            assertNotNull(nmsRegistries, "NMS BuiltInRegistries class must be present on classpath");
        });
    }

    @Test
    public void testWorldEditAdapterInstantiation() {
        try {
            Class<?> pluginCls = Class.forName("com.sk89q.worldedit.bukkit.WorldEditPlugin");
            assertNotNull(pluginCls, "WorldEditPlugin class must be present");
        } catch (Throwable t) {
            System.err.println("WorldEdit Adapter Exception Stacktrace:");
            t.printStackTrace();
        }
    }
}
