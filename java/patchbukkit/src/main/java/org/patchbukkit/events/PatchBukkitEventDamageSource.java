package org.patchbukkit.events;

import net.kyori.adventure.pointer.Pointers;
import org.bukkit.Location;
import org.bukkit.damage.DamageScaling;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A native event's damage source does not have a parallel NMS entity handle. */
final class PatchBukkitEventDamageSource implements DamageSource {
    private final DamageType type;
    private final Entity direct;
    private final Entity causing;

    PatchBukkitEventDamageSource(DamageType type, Entity direct, Entity causing) {
        this.type = type;
        this.direct = direct;
        this.causing = causing;
    }

    @Override public @NotNull DamageType getDamageType() { return this.type; }
    @Override public @Nullable Entity getDirectEntity() { return this.direct; }
    @Override public @Nullable Entity getCausingEntity() { return this.causing; }
    @Override public @Nullable Location getDamageLocation() { return null; }

    @Override
    public @Nullable Location getSourceLocation() {
        Entity source = this.direct != null ? this.direct : this.causing;
        return source != null ? source.getLocation() : null;
    }

    @Override
    public boolean isIndirect() {
        return this.direct != this.causing;
    }

    @Override public float getFoodExhaustion() { return this.type.getExhaustion(); }

    @Override
    public boolean scalesWithDifficulty() {
        return this.type.getDamageScaling() == DamageScaling.ALWAYS
                || (this.type.getDamageScaling() == DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER
                && this.causing instanceof LivingEntity && !(this.causing instanceof Player));
    }

    @Override public @NotNull Pointers getDamageContext() { return Pointers.empty(); }
}
