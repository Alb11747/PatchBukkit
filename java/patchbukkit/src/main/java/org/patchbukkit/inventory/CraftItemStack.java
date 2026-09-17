package org.patchbukkit.inventory;

import org.bukkit.inventory.ItemStack;

public class CraftItemStack extends ItemStack {

    public CraftItemStack() {
        super();
    }

    public static net.minecraft.world.item.ItemStack asNMSCopy(ItemStack original) {
        try {
            return org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(original);
        } catch (Throwable t) {
            return null;
        }
    }

    public static ItemStack asBukkitCopy(net.minecraft.world.item.ItemStack original) {
        try {
            return org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(original);
        } catch (Throwable t) {
            return null;
        }
    }

    public static ItemStack asCraftMirror(net.minecraft.world.item.ItemStack original) {
        try {
            return org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitMirror(original);
        } catch (Throwable t) {
            return null;
        }
    }
}
