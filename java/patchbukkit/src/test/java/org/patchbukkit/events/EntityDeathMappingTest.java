package org.patchbukkit.events;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.patchbukkit.PatchBukkitServer;
import org.patchbukkit.entity.PatchBukkitPlayer;
import patchbukkit.events.DeathContext;
import patchbukkit.events.Event;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EntityDeathMappingTest {
    private static final UUID WORLD = new UUID(10, 1);
    private static final UUID VICTIM = new UUID(10, 2);
    private static final UUID KILLER = new UUID(10, 3);
    private static Server original;
    private static Field serverField;
    private static Player victim;
    private static Player killer;

    @BeforeAll
    static void setUp() throws Exception {
        if (Bukkit.getServer() == null) Bukkit.setServer(new PatchBukkitServer());
        original = Bukkit.getServer();
        victim = new PatchBukkitPlayer(VICTIM, "Victim", 42);
        killer = new PatchBukkitPlayer(KILLER, "Killer", 43);
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUID" -> WORLD;
                    case "getName" -> "synthetic";
                    default -> null;
                });
        Server fixture = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorlds" -> List.of(world);
                    case "getWorld" -> WORLD.equals(args[0]) ? world : null;
                    case "getPlayer" -> VICTIM.equals(args[0]) ? victim : KILLER.equals(args[0]) ? killer : null;
                    default -> method.invoke(original, args);
                });
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, fixture);
    }

    @AfterAll
    static void restoreServer() throws Exception {
        serverField.set(null, original);
    }

    private static patchbukkit.common.UUID uuid(UUID id) {
        return patchbukkit.common.UUID.newBuilder().setValue(id.toString()).build();
    }

    private static DeathContext.Builder context() {
        return DeathContext.newBuilder().setEntityUuid(uuid(VICTIM)).setEntityType("minecraft:enderman")
                .setWorldUuid(uuid(WORLD)).setX(12.5).setY(70).setZ(-4.5).setDamageType("fall");
    }

    @Test
    void serializedMobDeathProvidesLivingIdentityCauseAndAuthoritativeKiller() {
        Event wire = Event.newBuilder().setEntityDeath(patchbukkit.events.EntityDeathEvent.newBuilder()
                .setEntityId(42).setDroppedExp(7).setContext(context().setKillerUuid(uuid(KILLER)))).build();
        EntityDeathEvent event = assertInstanceOf(EntityDeathEvent.class,
                PatchBukkitEventFactory.createEventFromBytes(wire.toByteArray()));
        assertNotNull(event.getEntity());
        assertEquals(VICTIM, event.getEntity().getUniqueId());
        assertEquals(EntityType.ENDERMAN, event.getEntity().getType());
        assertEquals(42, event.getEntity().getEntityId());
        assertEquals(12.5, event.getEntity().getLocation().getX());
        assertEquals(7, event.getDroppedExp());
        assertSame(killer, event.getEntity().getKiller());
        assertEquals(EntityDamageEvent.DamageCause.FALL, event.getEntity().getLastDamageCause().getCause());
        assertNull(event.getDamageSource().getCausingEntity(), "Kill credit is not a guessed fatal attacker");
    }

    @Test
    void playerDeathClearsPreviousKillCreditAndMapsMessageAndKeepInventory() {
        victim.setKiller(killer);
        Event wire = Event.newBuilder().setPlayerDeath(patchbukkit.events.PlayerDeathEvent.newBuilder()
                .setPlayerUuid(uuid(VICTIM)).setDeathMessage("{\"text\":\"synthetic death\"}")
                .setDroppedExp(3).setKeepInventory(true).setContext(context().setEntityType("minecraft:player"))).build();
        PlayerDeathEvent event = assertInstanceOf(PlayerDeathEvent.class,
                PatchBukkitEventFactory.createEventFromBytes(wire.toByteArray()));
        assertSame(victim, event.getEntity());
        assertNull(event.getEntity().getKiller());
        assertTrue(event.getKeepInventory());
        assertEquals(3, event.getDroppedExp());
        assertEquals(EntityDamageEvent.DamageCause.FALL, victim.getLastDamageCause().getCause());
        victim.setLastDamageCause(null);
        assertNull(victim.getLastDamageCause());
    }

    @Test
    void incompleteDeathPayloadNeverPublishesNullLivingEntity() {
        Event wire = Event.newBuilder().setEntityDeath(patchbukkit.events.EntityDeathEvent.newBuilder()
                .setEntityId(42)).build();
        assertNull(PatchBukkitEventFactory.createEventFromBytes(wire.toByteArray()));
    }

    @Test
    void cancelledDamageDoesNotReplaceStoredCause() {
        var source = new PatchBukkitEventDamageSource(
                new org.patchbukkit.registry.PatchBukkitDamageType(org.bukkit.NamespacedKey.minecraft("fall")), null, null);
        var previous = new EntityDamageEvent(victim, EntityDamageEvent.DamageCause.FALL, source, 2);
        victim.setLastDamageCause(previous);
        var current = new EntityDamageEvent(victim, EntityDamageEvent.DamageCause.LAVA, source, 3);
        current.setCancelled(true);
        PatchBukkitEventFactory.toFireEventResponse(current);
        assertSame(previous, victim.getLastDamageCause());
        current.setCancelled(false);
        PatchBukkitEventFactory.toFireEventResponse(current);
        assertSame(current, victim.getLastDamageCause());
    }
}
