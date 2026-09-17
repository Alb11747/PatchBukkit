package org.patchbukkit;

public class CraftParticle {

    public static org.bukkit.Particle minecraftToBukkit(net.minecraft.core.particles.ParticleType<?> nms) {
        return org.bukkit.craftbukkit.CraftParticle.minecraftToBukkit(nms);
    }

    public static net.minecraft.core.particles.ParticleType<?> bukkitToMinecraft(org.bukkit.Particle bukkit) {
        return org.bukkit.craftbukkit.CraftParticle.bukkitToMinecraft(bukkit);
    }
}
