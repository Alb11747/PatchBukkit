package org.patchbukkit.network;

import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import net.minecraft.SharedConstants;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.patchbukkit.PatchBukkitServer;
import org.patchbukkit.entity.PatchBukkitPlayer;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class HeadlessServerPlayerTest {

    private static sun.misc.Unsafe unsafe;

    @BeforeAll
    public static void setUp() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        PatchBukkitServer.initServer();

        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        unsafe = (sun.misc.Unsafe) f.get(null);
    }

    @Test
    public void testAllocateHeadlessServerPlayerHierarchy() throws Exception {
        UUID uuid = UUID.randomUUID();
        String name = "TestBot";
        PatchBukkitPlayer bukkitPlayer = new PatchBukkitPlayer(uuid, name);

        // 1. Virtual Channel
        PatchBukkitVirtualChannel channel = new PatchBukkitVirtualChannel(uuid);

        // 2. Net.minecraft.network.Connection
        Connection connection = (Connection) unsafe.allocateInstance(Connection.class);
        connection.channel = channel;
        connection.address = new InetSocketAddress("127.0.0.1", 54321);

        // Set receiving flow field
        for (Field cf : Connection.class.getDeclaredFields()) {
            if (cf.getType().equals(PacketFlow.class)) {
                cf.setAccessible(true);
                cf.set(connection, PacketFlow.SERVERBOUND);
                break;
            }
        }

        // 3. ServerPlayer
        ServerPlayer serverPlayer = (ServerPlayer) unsafe.allocateInstance(ServerPlayer.class);

        // Set Entity UUID
        Field entUuidField = net.minecraft.world.entity.Entity.class.getDeclaredField("uuid");
        entUuidField.setAccessible(true);
        entUuidField.set(serverPlayer, uuid);

        Field entStringUuidField = net.minecraft.world.entity.Entity.class.getDeclaredField("stringUUID");
        entStringUuidField.setAccessible(true);
        entStringUuidField.set(serverPlayer, uuid.toString());

        // Set gameProfile
        GameProfile profile = new GameProfile(uuid, name);
        Field profileField = null;
        Class<?> pClass = ServerPlayer.class;
        while (pClass != null && profileField == null) {
            for (Field pf : pClass.getDeclaredFields()) {
                if (pf.getType().equals(GameProfile.class)) {
                    profileField = pf;
                    break;
                }
            }
            pClass = pClass.getSuperclass();
        }
        assertNotNull(profileField, "GameProfile field must exist on Player/ServerPlayer");
        profileField.setAccessible(true);
        profileField.set(serverPlayer, profile);

        // 4. ServerGamePacketListenerImpl
        ServerGamePacketListenerImpl listener =
                (ServerGamePacketListenerImpl) unsafe.allocateInstance(ServerGamePacketListenerImpl.class);
        listener.player = serverPlayer;

        // Set server and connection in ServerCommonPacketListenerImpl superclass
        Class<?> scClass = ServerCommonPacketListenerImpl.class;
        Field serverField = scClass.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(listener, PatchBukkitServer.getDedicatedServer());

        Field connField = scClass.getDeclaredField("connection");
        connField.setAccessible(true);
        connField.set(listener, connection);

        // Link listener into ServerPlayer
        serverPlayer.connection = listener;

        // 5. CraftPlayer
        CraftPlayer craftPlayer = (CraftPlayer) unsafe.allocateInstance(CraftPlayer.class);
        Field entityField = null;
        Class<?> cpClass = CraftPlayer.class;
        while (cpClass != null && entityField == null) {
            for (Field ef : cpClass.getDeclaredFields()) {
                if (ef.getName().equals("entity")) {
                    entityField = ef;
                    break;
                }
            }
            cpClass = cpClass.getSuperclass();
        }
        assertNotNull(entityField, "entity field must exist on CraftEntity");
        entityField.setAccessible(true);
        entityField.set(craftPlayer, serverPlayer);

        // Set bukkitEntity on Entity
        Field bukkitEntityField = null;
        Class<?> entClass = net.minecraft.world.entity.Entity.class;
        for (Field bef : entClass.getDeclaredFields()) {
            if (bef.getName().equals("bukkitEntity")) {
                bukkitEntityField = bef;
                break;
            }
        }
        assertNotNull(bukkitEntityField, "bukkitEntity field must exist on Entity");
        bukkitEntityField.setAccessible(true);
        bukkitEntityField.set(serverPlayer, craftPlayer);

        // Assertions:
        // A. Channel retrieval matches
        assertSame(channel, serverPlayer.connection.connection.channel);
        assertSame(channel, listener.connection.channel);

        // B. getBukkitEntity() and getHandle() circular reference matches
        assertSame(craftPlayer, serverPlayer.getBukkitEntity());
        assertSame(serverPlayer, craftPlayer.getHandle());

        // C. UUID and Name matches
        assertEquals(uuid, serverPlayer.getUUID());
        assertEquals(name, serverPlayer.getScoreboardName());
        assertEquals(uuid, craftPlayer.getUniqueId());
        assertEquals(name, craftPlayer.getName());
    }
}
