package org.patchbukkit.entity;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Keeps authoritative death-event state available after the engine removes the entity. */
public final class PatchBukkitDeathEntity extends PatchBukkitLivingEntity {
    private final EntityType type;
    private final Location deathLocation;

    public PatchBukkitDeathEntity(UUID uuid, String name, int entityId, EntityType type, Location location) {
        super(uuid, name, entityId);
        this.type = type;
        this.deathLocation = location.clone();
    }

    @Override
    public @NotNull EntityType getType() {
        return this.type;
    }

    @Override
    public @NotNull Location getLocation() {
        return this.deathLocation.clone();
    }

    @Override
    public @NotNull World getWorld() {
        return this.deathLocation.getWorld();
    }

    @Override
    public double getHealth() {
        return 0.0;
    }

    @Override
    public boolean isDead() {
        return true;
    }

}
