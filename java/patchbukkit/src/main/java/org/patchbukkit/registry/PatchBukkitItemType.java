package org.patchbukkit.registry;

import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import patchbukkit.registry.ItemDefinition;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public final class PatchBukkitItemType {

    private PatchBukkitItemType() {}

    public static ItemType create(Material material) {
        if (material == null || material.isLegacy()) return null;
        return Registry.ITEM.get(material.getKey());
    }

    public static ItemType create(Material material, ItemDefinition item) {
        if (material == null || material.isLegacy()) return null;
        // Material delegates these properties to ItemType. The native registry supplies its defaults.

        return (ItemType) Proxy.newProxyInstance(
                PatchBukkitItemType.class.getClassLoader(),
                new Class<?>[]{ItemType.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getKey".equals(name) || "key".equals(name)) {
                        return material.getKey();
                    }
                    if ("asMaterial".equals(name)) {
                        return material;
                    }
                    if ("getMaxStackSize".equals(name)) {
                        return item.getMaxStackSize();
                    }
                    if ("getMaxDurability".equals(name)) {
                        return (short) item.getMaxDurability();
                    }
                    if ("isEdible".equals(name)) {
                        return item.getEdible();
                    }
                    if ("isRecord".equals(name)) {
                        return item.getRecord();
                    }
                    if ("createItemStack".equals(name)) {
                        int amount = (args != null && args.length > 0 && args[0] instanceof Integer i) ? i : 1;
                        return new org.patchbukkit.inventory.PatchBukkitItemStack(material, amount);
                    }
                    if ("equals".equals(name) && args != null && args.length == 1) {
                        if (args[0] instanceof Keyed k) {
                            return material.getKey().equals(k.getKey());
                        }
                        return false;
                    }
                    if ("hashCode".equals(name)) {
                        return material.getKey().hashCode();
                    }
                    if ("toString".equals(name)) {
                        return "ItemType{" + material.getKey() + "}";
                    }
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }

                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) return false;
                    if (returnType == int.class || returnType == short.class || returnType == long.class || returnType == byte.class) return 0;
                    if (returnType == float.class || returnType == double.class) return 0.0f;
                    return null;
                }
        );
    }
}
