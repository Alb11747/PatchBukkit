package org.patchbukkit.events;

import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.patchbukkit.entity.PatchBukkitDeathEntity;
import org.patchbukkit.registry.PatchBukkitDamageType;
import org.patchbukkit.world.PatchBukkitWorld;
import patchbukkit.events.DeathContext;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Explicit mappings for events whose entity and source must never be guessed by reflection. */
final class PatchBukkitEntityEvents {
    private PatchBukkitEntityEvents() {}

    static org.bukkit.event.entity.EntityDeathEvent death(patchbukkit.events.EntityDeathEvent event) {
        if (!event.hasContext()) throw new IllegalArgumentException("Missing native death context");
        var context = event.getContext();
        var entity = new PatchBukkitDeathEntity(UUID.fromString(context.getEntityUuid().getValue()),
                context.getEntityType(), event.getEntityId(), entityType(context), location(context));
        var source = prepareDeath(entity, context);
        return new org.bukkit.event.entity.EntityDeathEvent(entity, source, new ArrayList<>(), event.getDroppedExp());
    }

    static org.bukkit.event.entity.PlayerDeathEvent playerDeath(patchbukkit.events.PlayerDeathEvent event) {
        if (!event.hasContext()) throw new IllegalArgumentException("Missing native player death context");
        Player player = Objects.requireNonNull(PatchBukkitEventFactory.getPlayer(event.getPlayerUuid().getValue()));
        if (!player.getUniqueId().toString().equals(event.getContext().getEntityUuid().getValue())) {
            throw new IllegalArgumentException("Player death identity mismatch");
        }
        var source = prepareDeath(player, event.getContext());
        var death = new org.bukkit.event.entity.PlayerDeathEvent(player, source, new ArrayList<>(),
                event.getDroppedExp(), GsonComponentSerializer.gson().deserialize(event.getDeathMessage()),
                event.getContext().getShowDeathMessages());
        death.setKeepInventory(event.getKeepInventory());
        return death;
    }

    private static DamageSource prepareDeath(org.bukkit.entity.LivingEntity entity, DeathContext context) {
        // The engine already applies expiry/combat credit. Never reuse wrapper damage to infer a killer.
        entity.setKiller(context.hasKillerUuid()
                ? PatchBukkitEventFactory.getPlayer(context.getKillerUuid().getValue()) : null);
        var source = source(context.getDamageType(), null);
        entity.setLastDamageCause(context.getDamageType().isEmpty() ? null
                : new EntityDamageEvent(entity, cause(context.getDamageType()), source, 0.0));
        return source;
    }

    static EntityDamageEvent damage(patchbukkit.events.EntityDamageEvent event) {
        Entity entity = entity(event.getEntityId());
        return new EntityDamageEvent(entity, cause(event.getDamageType()),
                source(event.getDamageType(), null), event.getDamage());
    }

    static EntityDamageEvent damageByEntity(patchbukkit.events.EntityDamageByEntityEvent event) {
        Entity entity = entity(event.getEntityId());
        Entity damager = entity(event.getDamagerId());
        return new org.bukkit.event.entity.EntityDamageByEntityEvent(damager, entity,
                cause(event.getCause()), source(event.getCause(), damager), event.getDamage());
    }

    static EntityDamageEvent damageByBlock(patchbukkit.events.EntityDamageByBlockEvent event) {
        Entity entity = entity(event.getEntityId());
        var block = entity.getWorld().getBlockAt(event.getDamagerPosX(), event.getDamagerPosY(), event.getDamagerPosZ());
        return new org.bukkit.event.entity.EntityDamageByBlockEvent(block, block.getState(), entity,
                cause(event.getCause()), source(event.getCause(), null), event.getDamage());
    }

    private static Entity entity(int id) {
        for (World world : Bukkit.getWorlds()) {
            if (world instanceof PatchBukkitWorld bridgeWorld) {
                Entity entity = bridgeWorld.getEntity(id);
                if (entity != null) return entity;
            }
        }
        throw new IllegalArgumentException("Native event entity cannot be resolved: " + id);
    }

    private static Location location(DeathContext context) {
        World world = Objects.requireNonNull(Bukkit.getWorld(UUID.fromString(context.getWorldUuid().getValue())),
                "Native death world is not registered");
        return new Location(world, context.getX(), context.getY(), context.getZ());
    }

    private static EntityType entityType(DeathContext context) {
        String name = context.getEntityType().replace("minecraft:", "").toUpperCase(Locale.ROOT);
        EntityType type = EntityType.valueOf(name);
        if (!type.isAlive()) throw new IllegalArgumentException("Death target is not a living entity");
        return type;
    }

    private static DamageSource source(String type, Entity direct) {
        NamespacedKey name = NamespacedKey.fromString(damageTypeKey(type));
        if (name == null) throw new IllegalArgumentException("Invalid native damage type");
        return new PatchBukkitEventDamageSource(new PatchBukkitDamageType(name), direct, direct);
    }

    private static String damageTypeKey(String type) {
        String key = switch (type) {
            case "player" -> "player_attack";
            case "mob" -> "mob_attack";
            case "inFire" -> "in_fire";
            case "onFire" -> "on_fire";
            case "outOfWorld" -> "out_of_world";
            case "hotFloor" -> "hot_floor";
            case "inWall" -> "in_wall";
            case "flyIntoWall" -> "fly_into_wall";
            case "fallingBlock" -> "falling_block";
            case "fallingStalactite" -> "falling_stalactite";
            case "sweetBerryBush" -> "sweet_berry_bush";
            case "anvil" -> "falling_anvil";
            case "dryout" -> "dry_out";
            case "explosion.player" -> "player_explosion";
            default -> type.isEmpty() ? "generic" : type;
        };
        return key.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    static EntityDamageEvent.DamageCause cause(String type) {
        return switch (damageTypeKey(type)) {
            case "player", "mob", "player_attack", "mob_attack", "mob_attack_no_aggro", "sting" -> EntityDamageEvent.DamageCause.ENTITY_ATTACK;
            case "arrow", "trident", "fireball", "thrown", "wither_skull", "mob_projectile", "spit", "unattributed_fireball", "wind_charge" -> EntityDamageEvent.DamageCause.PROJECTILE;
            case "fall" -> EntityDamageEvent.DamageCause.FALL;
            case "drown" -> EntityDamageEvent.DamageCause.DROWNING;
            case "lava" -> EntityDamageEvent.DamageCause.LAVA;
            case "inFire", "in_fire" -> EntityDamageEvent.DamageCause.FIRE;
            case "onFire", "on_fire" -> EntityDamageEvent.DamageCause.FIRE_TICK;
            case "outOfWorld", "out_of_world" -> EntityDamageEvent.DamageCause.VOID;
            case "starve" -> EntityDamageEvent.DamageCause.STARVATION;
            case "inWall", "in_wall" -> EntityDamageEvent.DamageCause.SUFFOCATION;
            case "freeze" -> EntityDamageEvent.DamageCause.FREEZE;
            case "generic_kill" -> EntityDamageEvent.DamageCause.KILL;
            case "outside_border" -> EntityDamageEvent.DamageCause.WORLD_BORDER;
            case "hotFloor", "hot_floor" -> EntityDamageEvent.DamageCause.HOT_FLOOR;
            case "flyIntoWall", "fly_into_wall" -> EntityDamageEvent.DamageCause.FLY_INTO_WALL;
            case "cramming" -> EntityDamageEvent.DamageCause.CRAMMING;
            case "falling_anvil", "falling_block", "falling_stalactite" -> EntityDamageEvent.DamageCause.FALLING_BLOCK;
            case "lightning_bolt" -> EntityDamageEvent.DamageCause.LIGHTNING;
            case "wither" -> EntityDamageEvent.DamageCause.WITHER;
            case "thorns" -> EntityDamageEvent.DamageCause.THORNS;
            case "cactus", "sweetBerryBush", "sweet_berry_bush" -> EntityDamageEvent.DamageCause.CONTACT;
            case "explosion", "player_explosion" -> EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
            case "magic", "indirectMagic", "indirect_magic" -> EntityDamageEvent.DamageCause.MAGIC;
            default -> EntityDamageEvent.DamageCause.CUSTOM;
        };
    }
}
