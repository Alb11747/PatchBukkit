package org.patchbukkit.loader;

import io.papermc.paper.configuration.GlobalConfiguration;
import io.papermc.paper.configuration.PaperConfigurations;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class GlobalConfigTest {

    @BeforeAll
    public static void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void testPaperConfigurationsInit() throws Exception {
        Path tempDir = Files.createTempDirectory("paper-config-test");
        PaperConfigurations configs = new PaperConfigurations(tempDir);
        GlobalConfiguration globalConfig = configs.initializeGlobalConfiguration(RegistryAccess.EMPTY);
        assertNotNull(globalConfig);
        assertNotNull(GlobalConfiguration.get());
        assertNotNull(GlobalConfiguration.get().misc);
        assertEquals(5, GlobalConfiguration.get().misc.maxJoinsPerTick);

        Class<?> connClass = Class.forName("net.minecraft.network.Connection");
        assertNotNull(connClass);
    }

    @Test
    public void testServerConnectionListener() throws Exception {
        net.minecraft.server.network.ServerConnectionListener scl =
                new net.minecraft.server.network.ServerConnectionListener(null);
        assertNotNull(scl);
        assertNotNull(scl.getConnections());
    }

    @Test
    public void testDedicatedServerAllocation() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);

        Class<?> dsClass = Class.forName("net.minecraft.server.dedicated.DedicatedServer");
        Object dedicatedServer = unsafe.allocateInstance(dsClass);
        assertNotNull(dedicatedServer);

        net.minecraft.server.network.ServerConnectionListener scl =
                new net.minecraft.server.network.ServerConnectionListener((net.minecraft.server.MinecraftServer) dedicatedServer);

        Field connField = null;
        for (Field f : net.minecraft.server.MinecraftServer.class.getDeclaredFields()) {
            if (f.getType().equals(net.minecraft.server.network.ServerConnectionListener.class)) {
                connField = f;
                break;
            }
        }
        assertNotNull(connField);
        connField.setAccessible(true);
        connField.set(dedicatedServer, scl);

        assertSame(scl, connField.get(dedicatedServer));
    }

    @Test
    public void testCraftServerAllocation() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);

        Class<?> csClass = Class.forName("org.bukkit.craftbukkit.CraftServer");
        System.out.println("CraftServer code source: " + csClass.getProtectionDomain().getCodeSource().getLocation());
        for (java.lang.reflect.Constructor<?> ctor : csClass.getDeclaredConstructors()) {
            System.out.println("CraftServer ctor: " + ctor);
        }
        Object craftServer = unsafe.allocateInstance(csClass);
        assertNotNull(craftServer);
        assertTrue(craftServer instanceof org.bukkit.Server);
        assertEquals("org.bukkit.craftbukkit.CraftServer", craftServer.getClass().getName());
        assertEquals("org.bukkit.craftbukkit", craftServer.getClass().getPackage().getName());
    }
}
