package org.patchbukkit.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class MojangServerProviderTest {

    @Test
    public void testServerJarProviderLocatesOrDownloads(@TempDir File tempDir) {
        File serverJar = MojangServerProvider.getOrDownloadServerJar(tempDir, "26.2");
        assertNotNull(serverJar, "Server jar should be located or downloaded");
        assertTrue(serverJar.exists(), "Server jar file must exist");
        assertTrue(serverJar.length() > 0, "Server jar file must not be empty");

        // Second call should return cached file immediately
        long length = serverJar.length();
        File cached = MojangServerProvider.getOrDownloadServerJar(tempDir, "26.2");
        assertNotNull(cached);
        assertEquals(length, cached.length());
    }
}
