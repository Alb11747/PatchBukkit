package org.patchbukkit.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.patchbukkit.network.VirtualChannelManager;

public class CraftPlayer extends PatchBukkitPlayer {

    public CraftPlayer(UUID uuid, String name) {
        super(uuid, name);
    }

    public CraftPlayer(UUID uuid, String name, int entityId) {
        super(uuid, name, entityId);
    }

    public CraftPlayer(org.patchbukkit.PatchBukkitServer server, ServerPlayer entity) {
        super(entity.getUUID(), entity.getScoreboardName(), entity.getId());
    }

    @Override
    public ServerPlayer getHandle() {
        return VirtualChannelManager.getInstance().getOrCreateServerPlayer(this);
    }
}
