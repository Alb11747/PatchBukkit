package org.patchbukkit.entity;

import java.util.UUID;
import net.minecraft.world.entity.Entity;

public class CraftEntity extends PatchBukkitEntity {

    private Entity handle;

    public CraftEntity(UUID uuid, String name) {
        super(uuid, name);
    }

    public Entity getHandle() {
        return this.handle;
    }

    public void setHandle(Entity handle) {
        this.handle = handle;
    }
}
