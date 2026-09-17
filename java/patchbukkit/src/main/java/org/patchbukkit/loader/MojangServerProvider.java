package org.patchbukkit.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles downloading and caching official Mojang server bytecode at runtime.
 * This guarantees 100% authentic NMS classes and static reflection compliance
 * without violating Mojang's EULA by redistributing proprietary bytecode.
 */
public final class MojangServerProvider {

    private static final Logger LOGGER = Logger.getLogger("MojangServerProvider");
    private static final String MOJANG_MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private MojangServerProvider() {}

    /**
     * Ensures the official Mojang server jar is available in the target cache directory.
     *
     * @param cacheDir  the folder to store cached server jars (e.g., patchbukkit-libs)
     * @param mcVersion the target Minecraft version (e.g. "26.2" or "1.21.4")
     * @return the File pointing to the cached server jar, or null if download failed
     */
    public static File getOrDownloadServerJar(File cacheDir, String mcVersion) {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        String versionKey = (mcVersion != null && !mcVersion.isBlank()) ? mcVersion : "latest";
        File targetJar = new File(cacheDir, "mojang-server-" + versionKey + ".jar");

        if (targetJar.exists() && targetJar.length() > 0) {
            return targetJar;
        }

        // Development fallback: check if paperweight taskCache has mappedServerJar.jar
        File devMappedJar = findDevMappedServerJar();
        if (devMappedJar != null && devMappedJar.exists() && devMappedJar.length() > 0) {
            LOGGER.info("[PatchBukkit] Found local development mappedServerJar: " + devMappedJar.getAbsolutePath());
            try {
                Files.copy(devMappedJar.toPath(), targetJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return targetJar;
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Failed to copy local dev jar, proceeding to download: " + t.getMessage());
            }
        }

        try {
            LOGGER.info("[PatchBukkit] Downloading official Mojang server bytecode for Minecraft " + versionKey + "...");
            return downloadVanillaServer(targetJar, versionKey);
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "[PatchBukkit] Failed to obtain Mojang server jar: " + t.getMessage(), t);
            return null;
        }
    }

    private static File findDevMappedServerJar() {
        File[] possibleLocations = new File[] {
            new File(".gradle/caches/paperweight/taskCache/mappedServerJar.jar"),
            new File("java/patchbukkit/.gradle/caches/paperweight/taskCache/mappedServerJar.jar"),
            new File("../java/patchbukkit/.gradle/caches/paperweight/taskCache/mappedServerJar.jar")
        };
        for (File loc : possibleLocations) {
            if (loc.exists()) {
                return loc;
            }
        }
        return null;
    }

    private static File downloadVanillaServer(File targetFile, String version) throws Exception {
        // 1. Fetch Mojang version manifest
        HttpURLConnection conn = (HttpURLConnection) URI.create(MOJANG_MANIFEST_URL).toURL().openConnection();
        conn.setRequestProperty("User-Agent", "PatchBukkit-ServerProvider");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);

        JsonObject manifest;
        try (InputStream in = conn.getInputStream();
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            manifest = JsonParser.parseReader(reader).getAsJsonObject();
        }

        String targetUrl = null;
        String latestRelease = manifest.getAsJsonObject("latest").get("release").getAsString();

        JsonArray versions = manifest.getAsJsonArray("versions");
        for (JsonElement elem : versions) {
            JsonObject vObj = elem.getAsJsonObject();
            String id = vObj.get("id").getAsString();
            if (id.equalsIgnoreCase(version)) {
                targetUrl = vObj.get("url").getAsString();
                break;
            }
        }

        // Fallback to latest release if exact version tag not found
        if (targetUrl == null) {
            LOGGER.warning("[PatchBukkit] Version " + version + " not found in manifest; falling back to latest release: " + latestRelease);
            for (JsonElement elem : versions) {
                JsonObject vObj = elem.getAsJsonObject();
                if (vObj.get("id").getAsString().equalsIgnoreCase(latestRelease)) {
                    targetUrl = vObj.get("url").getAsString();
                    break;
                }
            }
        }

        if (targetUrl == null) {
            throw new IllegalStateException("Could not resolve download metadata for Minecraft version: " + version);
        }

        // 2. Fetch version details
        HttpURLConnection vConn = (HttpURLConnection) URI.create(targetUrl).toURL().openConnection();
        vConn.setRequestProperty("User-Agent", "PatchBukkit-ServerProvider");
        vConn.setConnectTimeout(10000);
        vConn.setReadTimeout(15000);

        JsonObject vDetails;
        try (InputStream in = vConn.getInputStream();
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            vDetails = JsonParser.parseReader(reader).getAsJsonObject();
        }

        JsonObject serverDownload = vDetails.getAsJsonObject("downloads").getAsJsonObject("server");
        String serverJarUrl = serverDownload.get("url").getAsString();
        String expectedSha1 = serverDownload.get("sha1").getAsString();

        // 3. Download server.jar with SHA-1 verification
        File tempFile = new File(targetFile.getParentFile(), targetFile.getName() + ".downloading");
        MessageDigest digest = MessageDigest.getInstance("SHA-1");

        HttpURLConnection downloadConn = (HttpURLConnection) URI.create(serverJarUrl).toURL().openConnection();
        downloadConn.setRequestProperty("User-Agent", "PatchBukkit-ServerProvider");
        downloadConn.setConnectTimeout(15000);
        downloadConn.setReadTimeout(30000);

        try (InputStream in = new BufferedInputStream(downloadConn.getInputStream());
             FileOutputStream out = new FileOutputStream(tempFile)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
                digest.update(buf, 0, read);
            }
        }

        String actualSha1 = HexFormat.of().formatHex(digest.digest());
        if (!actualSha1.equalsIgnoreCase(expectedSha1)) {
            tempFile.delete();
            throw new SecurityException("SHA-1 checksum mismatch for " + serverJarUrl + "! Expected: " + expectedSha1 + ", got: " + actualSha1);
        }

        Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        LOGGER.info("[PatchBukkit] Successfully downloaded and verified official Mojang server bytecode (" + targetFile.length() / (1024 * 1024) + " MB).");
        return targetFile;
    }
}
