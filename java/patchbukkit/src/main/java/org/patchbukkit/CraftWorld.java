package org.patchbukkit;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.patchbukkit.world.PatchBukkitWorld;

public class CraftWorld extends PatchBukkitWorld {

    private ServerLevel handle;

    public CraftWorld(UUID uuid) {
        super(uuid);
    }

    public ServerLevel getHandle() {
        if (this.handle == null) {
            try {
                java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
                this.handle = (ServerLevel) unsafe.allocateInstance(ServerLevel.class);
            } catch (Throwable ignored) {}
        }
        return this.handle;
    }
}
