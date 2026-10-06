package org.patchbukkit.registry;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemType;
import patchbukkit.registry.ItemDefinition;

import java.util.Map;

/** Supplies a small vanilla-shaped fixture at the native registry boundary for JVM-only tests. */
public final class NativeItemFixture {
    private NativeItemFixture() {}

    @SuppressWarnings("unchecked")
    public static void install() throws ReflectiveOperationException {
        PatchBukkitRegistry<?, ItemType> registry = (PatchBukkitRegistry<?, ItemType>) Registry.ITEM;
        var initializedField = PatchBukkitRegistry.class.getDeclaredField("initialized");
        initializedField.setAccessible(true);
        initializedField.setBoolean(registry, true);
        var entriesField = PatchBukkitRegistry.class.getDeclaredField("entries");
        entriesField.setAccessible(true);
        Map<NamespacedKey, ItemType> entries = (Map<NamespacedKey, ItemType>) entriesField.get(registry);
        add(entries, Material.AIR, 64, 0, false, false);
        add(entries, Material.STONE, 64, 0, false, false);
        add(entries, Material.ENDER_PEARL, 16, 0, false, false);
        add(entries, Material.DIAMOND_SWORD, 1, 1561, false, false);
        add(entries, Material.APPLE, 64, 0, true, false);
        add(entries, Material.MUSIC_DISC_13, 1, 0, false, true);
    }

    private static void add(Map<NamespacedKey, ItemType> entries, Material material, int stackSize, int durability, boolean edible, boolean record) {
        ItemDefinition definition = ItemDefinition.newBuilder()
                .setName(material.getKey().toString()).setMaxStackSize(stackSize).setMaxDurability(durability)
                .setEdible(edible).setRecord(record).build();
        entries.put(material.getKey(), PatchBukkitItemType.create(material, definition));
    }
}
