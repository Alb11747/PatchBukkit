package org.patchbukkit;

import com.destroystokyo.paper.SkinParts;
import com.destroystokyo.paper.util.VersionFetcher;
import io.papermc.paper.InternalAPIBridge;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.entity.poi.PoiType;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.world.damagesource.CombatEntry;
import io.papermc.paper.world.damagesource.FallLocationType;
import java.io.IOException;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.attribute.Attributable;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.damage.DamageEffect;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

public class PatchBukkitInternalAPIBridge implements InternalAPIBridge {
    public ComponentFlattener componentFlattener() {
        return ComponentFlattener.basic();
    }

    public Component resolveWithContext(
        Component component,
        @Nullable CommandSender context,
        @Nullable Entity entity,
        boolean hasPermission
    ) throws IOException {
        return component;
    }

    private static final java.lang.reflect.Constructor<ItemStack> ITEM_STACK_NO_ARG_CTOR;

    static {
        try {
            ITEM_STACK_NO_ARG_CTOR = ItemStack.class.getDeclaredConstructor();
            ITEM_STACK_NO_ARG_CTOR.setAccessible(true);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public ItemStack createEmptyStack() {
        try {
            return ITEM_STACK_NO_ARG_CTOR.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create empty ItemStack", e);
        }
    }

    public LifecycleEventManager<Plugin> createPluginLifecycleEventManager(
        JavaPlugin plugin,
        BooleanSupplier registrationCheck
    ) {
        return new org.patchbukkit.events.PatchBukkitLifecycleEventManager(plugin, registrationCheck);
    }

    public String getStatisticCriteriaKey(Statistic statistic) {
        return statistic.name().toLowerCase();
    }

    public @Nullable Attributable getDefaultEntityAttributes(NamespacedKey key) {
        return null;
    }

    public boolean hasDefaultEntityAttributes(NamespacedKey key) {
        return false;
    }

    public ItemStack deserializeItem(byte[] bytes) {
        return createEmptyStack();
    }

    public String getTranslationKey(EntityType entityType) {
        return entityType.translationKey();
    }

    public org.bukkit.entity.SpawnCategory getSpawnCategory(EntityType entityType) {
        return org.bukkit.entity.SpawnCategory.MISC;
    }

    public @Nullable DamageEffect getDamageEffect(String key) {
        if (key == null) return null;
        try {
            var craft = org.bukkit.craftbukkit.damage.CraftDamageEffect.getById(key);
            if (craft != null) return craft;
        } catch (Throwable ignored) {}

        return switch (key.toLowerCase(java.util.Locale.ROOT)) {
            case "hurt" -> () -> org.bukkit.Sound.ENTITY_PLAYER_HURT;
            case "thorns" -> () -> org.bukkit.Sound.ENCHANT_THORNS_HIT;
            case "drowning" -> () -> org.bukkit.Sound.ENTITY_PLAYER_HURT_DROWN;
            case "burning" -> () -> org.bukkit.Sound.ENTITY_PLAYER_HURT_ON_FIRE;
            case "poking" -> () -> org.bukkit.Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH;
            case "freezing" -> () -> org.bukkit.Sound.ENTITY_PLAYER_HURT_FREEZE;
            default -> () -> org.bukkit.Sound.ENTITY_PLAYER_HURT;
        };
    }

    public DamageSource.Builder createDamageSourceBuilder(DamageType damageType) {
        try {
            return new org.bukkit.craftbukkit.damage.CraftDamageSourceBuilder(damageType);
        } catch (Throwable t) {
            return null;
        }
    }

    public PoiType.Occupancy createOccupancy(String type) {
        return null;
    }

    public Set<Pose> validMannequinPoses() {
        return Set.of();
    }

    public <MODERN, LEGACY> GameRule<LEGACY> legacyGameRuleBridge(
        GameRule<MODERN> modernRule,
        Function<LEGACY, MODERN> toModern,
        Function<MODERN, LEGACY> toLegacy,
        Class<LEGACY> legacyType
    ) {
        return null;
    }

    public Component defaultMannequinDescription() {
        return Component.empty();
    }

    public SkinParts.Mutable allSkinParts() {
        return null;
    }

    public ResolvableProfile defaultMannequinProfile() {
        return null;
    }

    public Predicate<CommandSourceStack> restricted(Predicate<CommandSourceStack> predicate) {
        return predicate;
    }

    public CombatEntry createCombatEntry(
        DamageSource damageSource,
        float damage,
        FallLocationType fallLocationType,
        float fallDistance
    ) {
        return null;
    }

    public CombatEntry createCombatEntry(
        LivingEntity entity,
        DamageSource damageSource,
        float damage
    ) {
        return null;
    }

    public Biome constructLegacyCustomBiome() {
        return null;
    }
}
