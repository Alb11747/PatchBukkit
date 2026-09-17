package org.patchbukkit.util;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Item;
import org.bukkit.Material;

public class CraftMagicNumbers {

    public static BlockState getBlock(org.bukkit.material.MaterialData material) {
        return org.bukkit.craftbukkit.util.CraftMagicNumbers.getBlock(material);
    }

    public static Block getBlock(Material material) {
        return org.bukkit.craftbukkit.util.CraftMagicNumbers.getBlock(material);
    }

    public static Material getMaterial(Block block) {
        return org.bukkit.craftbukkit.util.CraftMagicNumbers.getMaterial(block);
    }

    public static Item getItem(Material material) {
        return org.bukkit.craftbukkit.util.CraftMagicNumbers.getItem(material);
    }

    public static Material getMaterial(Item item) {
        return org.bukkit.craftbukkit.util.CraftMagicNumbers.getMaterial(item);
    }
}
