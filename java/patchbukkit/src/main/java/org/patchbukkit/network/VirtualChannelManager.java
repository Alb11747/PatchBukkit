package org.patchbukkit.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.patchbukkit.entity.PatchBukkitPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the lifecycle and I/O of virtual Netty channels for connected players.
 */
public final class VirtualChannelManager {

    private static final Logger LOGGER = Logger.getLogger("VirtualChannelManager");
    private static final VirtualChannelManager INSTANCE = new VirtualChannelManager();

    private final Map<UUID, PatchBukkitVirtualChannel> channels = new ConcurrentHashMap<>();
    private final Map<UUID, VirtualPlayerHandle> playerHandles = new ConcurrentHashMap<>();
    private final Map<UUID, net.minecraft.server.level.ServerPlayer> serverPlayers = new ConcurrentHashMap<>();

    private VirtualChannelManager() {}

    public static VirtualChannelManager getInstance() {
        return INSTANCE;
    }

    public PatchBukkitVirtualChannel getOrCreateChannel(UUID uuid) {
        if (uuid == null) return null;
        return channels.computeIfAbsent(uuid, PatchBukkitVirtualChannel::new);
    }

    public VirtualPlayerHandle getPlayerHandle(PatchBukkitPlayer player) {
        if (player == null) return null;
        return playerHandles.computeIfAbsent(player.getUniqueId(), uuid -> {
            PatchBukkitVirtualChannel channel = getOrCreateChannel(uuid);
            VirtualNetworkManager netManager = new VirtualNetworkManager(channel);
            VirtualPlayerConnection conn = new VirtualPlayerConnection(netManager);
            return new VirtualPlayerHandle(player, conn);
        });
    }

    public synchronized net.minecraft.server.level.ServerPlayer getOrCreateServerPlayer(PatchBukkitPlayer player) {
        if (player == null) return null;
        UUID uuid = player.getUniqueId();
        net.minecraft.server.level.ServerPlayer existing = serverPlayers.get(uuid);
        if (existing != null) return existing;

        try {
            java.lang.reflect.Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);

            PatchBukkitVirtualChannel channel = getOrCreateChannel(uuid);

            net.minecraft.network.Connection connection =
                    (net.minecraft.network.Connection) unsafe.allocateInstance(net.minecraft.network.Connection.class);
            connection.channel = channel;
            connection.address = player.getAddress() != null ? player.getAddress() : new java.net.InetSocketAddress("127.0.0.1", 54321);

            for (java.lang.reflect.Field cf : net.minecraft.network.Connection.class.getDeclaredFields()) {
                if (cf.getType().equals(net.minecraft.network.protocol.PacketFlow.class)) {
                    cf.setAccessible(true);
                    cf.set(connection, net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
                    break;
                }
            }

            net.minecraft.server.level.ServerPlayer serverPlayer =
                    (net.minecraft.server.level.ServerPlayer) unsafe.allocateInstance(net.minecraft.server.level.ServerPlayer.class);
            serverPlayer.setId(player.getEntityId());

            java.lang.reflect.Field entUuidField = net.minecraft.world.entity.Entity.class.getDeclaredField("uuid");
            entUuidField.setAccessible(true);
            entUuidField.set(serverPlayer, uuid);

            java.lang.reflect.Field entStringUuidField = net.minecraft.world.entity.Entity.class.getDeclaredField("stringUUID");
            entStringUuidField.setAccessible(true);
            entStringUuidField.set(serverPlayer, uuid.toString());

            com.mojang.authlib.GameProfile profile =
                    new com.mojang.authlib.GameProfile(uuid, player.getName() != null ? player.getName() : "Player");
            java.lang.reflect.Field profileField = null;
            Class<?> pClass = net.minecraft.server.level.ServerPlayer.class;
            while (pClass != null && profileField == null) {
                for (java.lang.reflect.Field pf : pClass.getDeclaredFields()) {
                    if (pf.getType().equals(com.mojang.authlib.GameProfile.class)) {
                        profileField = pf;
                        break;
                    }
                }
                pClass = pClass.getSuperclass();
            }
            if (profileField != null) {
                profileField.setAccessible(true);
                profileField.set(serverPlayer, profile);
            }

            net.minecraft.server.network.ServerGamePacketListenerImpl listener =
                    (net.minecraft.server.network.ServerGamePacketListenerImpl) unsafe.allocateInstance(net.minecraft.server.network.ServerGamePacketListenerImpl.class);
            listener.player = serverPlayer;

            Class<?> scClass = net.minecraft.server.network.ServerCommonPacketListenerImpl.class;
            java.lang.reflect.Field serverField = scClass.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(listener, org.patchbukkit.PatchBukkitServer.getDedicatedServer());

            java.lang.reflect.Field connField = scClass.getDeclaredField("connection");
            connField.setAccessible(true);
            connField.set(listener, connection);

            serverPlayer.connection = listener;

            try {
                org.bukkit.craftbukkit.entity.CraftPlayer craftPlayer =
                        (org.bukkit.craftbukkit.entity.CraftPlayer) unsafe.allocateInstance(org.bukkit.craftbukkit.entity.CraftPlayer.class);
                java.lang.reflect.Field entityField = null;
                Class<?> cpClass = org.bukkit.craftbukkit.entity.CraftPlayer.class;
                while (cpClass != null && entityField == null) {
                    for (java.lang.reflect.Field ef : cpClass.getDeclaredFields()) {
                        if (ef.getName().equals("entity")) {
                            entityField = ef;
                            break;
                        }
                    }
                    cpClass = cpClass.getSuperclass();
                }
                if (entityField != null) {
                    entityField.setAccessible(true);
                    entityField.set(craftPlayer, serverPlayer);
                }

                java.lang.reflect.Field bukkitEntityField = net.minecraft.world.entity.Entity.class.getDeclaredField("bukkitEntity");
                bukkitEntityField.setAccessible(true);
                bukkitEntityField.set(serverPlayer, craftPlayer);
            } catch (Throwable t) {
                LOGGER.log(Level.FINE, "[VirtualChannelManager] Failed to link CraftPlayer", t);
            }

            net.minecraft.server.network.ServerConnectionListener scl = org.patchbukkit.PatchBukkitServer.getServerConnection();
            if (scl != null && scl.getConnections() != null) {
                scl.getConnections().add(connection);
            }

            net.minecraft.server.dedicated.DedicatedPlayerList pl = org.patchbukkit.PatchBukkitServer.getDedicatedPlayerList();
            if (pl != null) {
                if (pl.getPlayers() != null) {
                    pl.getPlayers().add(serverPlayer);
                }
                try {
                    java.lang.reflect.Field byUuidField = null;
                    Class<?> curPl = pl.getClass();
                    while (curPl != null && byUuidField == null) {
                        for (java.lang.reflect.Field f : curPl.getDeclaredFields()) {
                            if (f.getName().equals("playersByUUID")) {
                                byUuidField = f;
                                break;
                            }
                        }
                        curPl = curPl.getSuperclass();
                    }
                    if (byUuidField != null) {
                        byUuidField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        Map<UUID, net.minecraft.server.level.ServerPlayer> map =
                                (Map<UUID, net.minecraft.server.level.ServerPlayer>) byUuidField.get(pl);
                        if (map != null) {
                            map.put(uuid, serverPlayer);
                        }
                    }
                } catch (Throwable ignored) {}
            }

            serverPlayers.put(uuid, serverPlayer);
            return serverPlayer;
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[VirtualChannelManager] Failed to allocate ServerPlayer for " + uuid, t);
            return null;
        }
    }

    public void removePlayer(UUID uuid) {
        if (uuid == null) return;
        PatchBukkitVirtualChannel channel = channels.remove(uuid);
        playerHandles.remove(uuid);
        net.minecraft.server.level.ServerPlayer sp = serverPlayers.remove(uuid);

        if (sp != null) {
            if (sp.connection != null && sp.connection.connection != null) {
                net.minecraft.server.network.ServerConnectionListener scl = org.patchbukkit.PatchBukkitServer.getServerConnection();
                if (scl != null && scl.getConnections() != null) {
                    scl.getConnections().remove(sp.connection.connection);
                }
            }
            net.minecraft.server.dedicated.DedicatedPlayerList pl = org.patchbukkit.PatchBukkitServer.getDedicatedPlayerList();
            if (pl != null) {
                if (pl.getPlayers() != null) {
                    pl.getPlayers().removeIf(p -> {
                        try {
                            return sp == p || uuid.equals(p.getUUID());
                        } catch (Throwable ignored) {
                            return false;
                        }
                    });
                }
                try {
                    java.lang.reflect.Field byUuidField = null;
                    Class<?> curPl = pl.getClass();
                    while (curPl != null && byUuidField == null) {
                        for (java.lang.reflect.Field f : curPl.getDeclaredFields()) {
                            if (f.getName().equals("playersByUUID")) {
                                byUuidField = f;
                                break;
                            }
                        }
                        curPl = curPl.getSuperclass();
                    }
                    if (byUuidField != null) {
                        byUuidField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        Map<UUID, net.minecraft.server.level.ServerPlayer> map =
                                (Map<UUID, net.minecraft.server.level.ServerPlayer>) byUuidField.get(pl);
                        if (map != null) {
                            map.remove(uuid);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }

        if (channel != null && channel.isOpen()) {
            channel.close();
        }
    }

    public void handlePacketReceived(UUID uuid, int packetId, byte[] payload) {
        if (uuid == null || payload == null) return;
        PatchBukkitVirtualChannel channel = getOrCreateChannel(uuid);
        if (channel == null || !channel.isActive()) return;

        try {
            ByteBuf buffer = Unpooled.wrappedBuffer(payload);
            channel.pipeline().fireChannelRead(buffer);
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[VirtualChannelManager] Error in inbound packet processing for " + uuid, t);
        }
    }

    public void handlePacketSent(UUID uuid, int packetId, byte[] payload) {
        if (uuid == null || payload == null) return;
        PatchBukkitVirtualChannel channel = getOrCreateChannel(uuid);
        if (channel == null || !channel.isActive()) return;

        try {
            ByteBuf buffer = Unpooled.wrappedBuffer(payload);
            channel.write(buffer);
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[VirtualChannelManager] Error in outbound packet processing for " + uuid, t);
        }
    }
}
