package org.patchbukkit.network;

import org.patchbukkit.entity.PatchBukkitPlayer;

import java.util.UUID;

/**
 * Acts as the NMS ServerPlayer / EntityPlayer handle returned by Player.getHandle().
 */
public class VirtualPlayerHandle {

    public final PatchBukkitPlayer player;
    public final UUID uuid;
    public final String name;

    // Mojang field name: connection
    public final VirtualPlayerConnection connection;

    // Spigot / legacy field name: playerConnection
    public final VirtualPlayerConnection playerConnection;

    public VirtualPlayerHandle(PatchBukkitPlayer player, VirtualPlayerConnection connection) {
        this.player = player;
        this.uuid = player.getUniqueId();
        this.name = player.getName();
        this.connection = connection;
        this.playerConnection = connection;
    }

    public PatchBukkitPlayer getBukkitEntity() {
        return player;
    }

    public UUID getUUID() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public VirtualPlayerConnection getConnection() {
        return connection;
    }
}
