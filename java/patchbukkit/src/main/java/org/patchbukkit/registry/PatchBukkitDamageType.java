package org.patchbukkit.registry;

import io.papermc.paper.util.Holderable;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.damage.CraftDamageEffect;
import org.bukkit.craftbukkit.damage.CraftDamageType;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.damage.DamageEffect;
import org.bukkit.damage.DamageScaling;
import org.bukkit.damage.DamageType;
import org.bukkit.damage.DeathMessageType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Objects;

public class PatchBukkitDamageType implements DamageType, Holderable<net.minecraft.world.damagesource.DamageType> {

    private static final Field HOLDER_REF_VALUE_FIELD;

    static {
        Field field = null;
        try {
            field = net.minecraft.core.Holder.Reference.class.getDeclaredField("value");
            field.setAccessible(true);
        } catch (Throwable ignored) {}
        HOLDER_REF_VALUE_FIELD = field;
    }

    private final NamespacedKey namespacedKey;
    private final Key adventureKey;
    private final String translationKey;
    private final DamageScaling damageScaling;
    private final DamageEffect damageEffect;
    private final DeathMessageType deathMessageType;
    private final float exhaustion;
    private final net.minecraft.world.damagesource.DamageType nmsHandle;
    private final net.minecraft.core.Holder<net.minecraft.world.damagesource.DamageType> holder;

    public PatchBukkitDamageType(@NonNull NamespacedKey key) {
        this(
            key,
            "death.attack." + (key != null ? key.getKey() : "generic"),
            determineScaling(key),
            determineEffect(key),
            determineDeathMessageType(key),
            determineExhaustion(key)
        );
    }

    public PatchBukkitDamageType(
            @NonNull NamespacedKey namespacedKey,
            @Nullable String translationKey,
            @Nullable DamageScaling damageScaling,
            @Nullable DamageEffect damageEffect,
            @Nullable DeathMessageType deathMessageType,
            float exhaustion
    ) {
        if (namespacedKey == null) {
            namespacedKey = NamespacedKey.minecraft("generic");
        }
        this.namespacedKey = namespacedKey;
        this.adventureKey = Key.key(namespacedKey.namespace(), namespacedKey.value());
        this.translationKey = translationKey != null ? translationKey : ("death.attack." + namespacedKey.getKey());
        this.damageScaling = damageScaling != null ? damageScaling : DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER;
        this.damageEffect = damageEffect != null ? damageEffect : getFallbackEffect();
        this.deathMessageType = deathMessageType != null ? deathMessageType : DeathMessageType.DEFAULT;
        this.exhaustion = exhaustion;

        net.minecraft.world.damagesource.DamageType nms = null;
        net.minecraft.core.Holder<net.minecraft.world.damagesource.DamageType> h = null;
        try {
            var scalingNms = CraftDamageType.damageScalingToNMS(this.damageScaling);
            var effectsNms = this.damageEffect instanceof CraftDamageEffect craftEffect
                ? craftEffect.getHandle()
                : net.minecraft.world.damagesource.DamageEffects.HURT;
            var deathMessageNms = CraftDamageType.deathMessageTypeToNMS(this.deathMessageType);
            nms = new net.minecraft.world.damagesource.DamageType(
                this.namespacedKey.getKey(),
                scalingNms,
                this.exhaustion,
                effectsNms,
                deathMessageNms
            );

            var nmsKey = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                CraftNamespacedKey.toMinecraft(this.namespacedKey)
            );
            var ref = net.minecraft.core.Holder.Reference.createStandAlone(null, nmsKey);
            if (HOLDER_REF_VALUE_FIELD != null) {
                HOLDER_REF_VALUE_FIELD.set(ref, nms);
            }
            h = ref;
        } catch (Throwable ignored) {}

        this.nmsHandle = nms;
        this.holder = h;
    }

    private static DamageEffect getFallbackEffect() {
        try {
            if (DamageEffect.HURT != null) {
                return DamageEffect.HURT;
            }
        } catch (Throwable ignored) {}
        return () -> org.bukkit.Sound.ENTITY_PLAYER_HURT;
    }

    private static DamageScaling determineScaling(@Nullable NamespacedKey key) {
        if (key == null) return DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER;
        String val = key.getKey().toLowerCase(Locale.ROOT);
        return switch (val) {
            case "fall", "drown", "starve", "freeze", "in_fire", "dry_out", "in_wall",
                 "cramming", "fly_into_wall", "stalagmite", "out_of_world", "outside_border",
                 "generic_kill" -> DamageScaling.NEVER;
            case "player_attack", "player_explosion", "explosion" -> DamageScaling.ALWAYS;
            default -> DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER;
        };
    }

    private static DamageEffect determineEffect(@Nullable NamespacedKey key) {
        if (key == null) return getFallbackEffect();
        String val = key.getKey().toLowerCase(Locale.ROOT);
        try {
            return switch (val) {
                case "in_fire", "on_fire", "lava", "campfire", "hot_floor" ->
                    DamageEffect.BURNING != null ? DamageEffect.BURNING : (() -> org.bukkit.Sound.ENTITY_PLAYER_HURT_ON_FIRE);
                case "drown" ->
                    DamageEffect.DROWNING != null ? DamageEffect.DROWNING : (() -> org.bukkit.Sound.ENTITY_PLAYER_HURT_DROWN);
                case "freeze" ->
                    DamageEffect.FREEZING != null ? DamageEffect.FREEZING : (() -> org.bukkit.Sound.ENTITY_PLAYER_HURT_FREEZE);
                case "cactus", "sweet_berry_bush", "stalagmite" ->
                    DamageEffect.POKING != null ? DamageEffect.POKING : (() -> org.bukkit.Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH);
                case "thorns" ->
                    DamageEffect.THORNS != null ? DamageEffect.THORNS : (() -> org.bukkit.Sound.ENCHANT_THORNS_HIT);
                default -> getFallbackEffect();
            };
        } catch (Throwable ignored) {
            return getFallbackEffect();
        }
    }

    private static DeathMessageType determineDeathMessageType(@Nullable NamespacedKey key) {
        if (key == null) return DeathMessageType.DEFAULT;
        String val = key.getKey().toLowerCase(Locale.ROOT);
        return switch (val) {
            case "bad_respawn_point" -> DeathMessageType.INTENTIONAL_GAME_DESIGN;
            case "fall", "stalagmite" -> DeathMessageType.FALL_VARIANTS;
            default -> DeathMessageType.DEFAULT;
        };
    }

    private static float determineExhaustion(@Nullable NamespacedKey key) {
        if (key == null) return 0.1f;
        String val = key.getKey().toLowerCase(Locale.ROOT);
        return switch (val) {
            case "freeze", "starve" -> 0.0f;
            default -> 0.1f;
        };
    }

    @Override
    public net.minecraft.core.Holder<net.minecraft.world.damagesource.DamageType> getHolder() {
        return holder;
    }

    @Override
    public net.minecraft.world.damagesource.DamageType getHandle() {
        return nmsHandle;
    }

    @Override
    public @NonNull NamespacedKey getKey() {
        return namespacedKey;
    }

    @Override
    public @NonNull Key key() {
        return adventureKey;
    }

    @Override
    public @NonNull String getTranslationKey() {
        return translationKey;
    }

    @Override
    public @NonNull DamageScaling getDamageScaling() {
        return damageScaling;
    }

    @Override
    public @NonNull DamageEffect getDamageEffect() {
        return damageEffect;
    }

    @Override
    public @NonNull DeathMessageType getDeathMessageType() {
        return deathMessageType;
    }

    @Override
    public float getExhaustion() {
        return exhaustion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DamageType that)) return false;
        return Objects.equals(namespacedKey, that.getKey());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(namespacedKey);
    }

    @Override
    public String toString() {
        return "PatchBukkitDamageType{" + namespacedKey + "}";
    }
}
