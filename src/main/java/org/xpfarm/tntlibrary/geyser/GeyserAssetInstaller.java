/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.geyser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Installs the bomb Geyser Custom Blocks assets — the {@code custom_mappings} JSON and the Bedrock
 * resource pack — into Geyser's plugin folder so a Bedrock client renders placed bombs as true cubes.
 *
 * <h2>Why {@code onLoad}, and why files (not the API)</h2>
 *
 * <p>Geyser reads {@code custom_mappings} once, at its own startup, and vanilla block-state overrides
 * <em>cannot</em> be registered through Geyser's Java API (Geyser issue #4177). So the only path is to
 * put the files on disk before Geyser initialises. TNTLibrary declares {@code loadbefore:
 * [Geyser-Spigot]}, so this runs in {@code onLoad} — before Geyser loads — and the files are present
 * when Geyser reads them. On a first install where Geyser's data folder does not exist yet, this
 * creates it from the detected Geyser JAR; a single restart may still be needed if Geyser initialised
 * before this ever ran.
 *
 * <h2>Failure is non-fatal</h2>
 *
 * <p>If no Geyser is detected, or copying fails, this logs and returns — it never throws, so a
 * Java-only server (no Geyser) enables normally. Geyser stays a soft dependency.
 *
 * <p>Server-dependent (filesystem, plugin JAR), so it is exercised at the runtime gate.
 */
public final class GeyserAssetInstaller {

    /** Prefix of the bundled resources this installer ships. */
    private static final String RESOURCE_ROOT = "geyser/";

    /** Bundled sub-path holding the Geyser mapping JSON(s). */
    private static final String MAPPINGS_PREFIX = RESOURCE_ROOT + "custom_mappings/";

    /** Bundled sub-path holding the Bedrock resource pack. */
    private static final String PACK_PREFIX = RESOURCE_ROOT + "pack/";

    /** Folder name the Bedrock pack is written into, under Geyser's {@code packs/}. */
    private static final String PACK_FOLDER = "tnt_library";

    private final Logger logger;
    private final File jarFile;
    private final File pluginsDir;

    /**
     * @param logger     the plugin logger
     * @param jarFile    this plugin's own JAR (its bundled {@code geyser/} resources are the source)
     * @param pluginsDir the server {@code plugins/} directory (Geyser's folder is a sibling)
     */
    public GeyserAssetInstaller(Logger logger, File jarFile, File pluginsDir) {
        this.logger = logger;
        this.jarFile = jarFile;
        this.pluginsDir = pluginsDir;
    }

    /** Detects Geyser and, if present, writes the mapping + Bedrock pack. Never throws. */
    public void install() {
        File geyserDir = locateGeyserFolder();
        if (geyserDir == null) {
            logger.fine("Geyser not detected; skipping custom-block asset install (Java-only server).");
            return;
        }
        try (ZipFile jar = new ZipFile(jarFile)) {
            int written = 0;
            Enumeration<? extends ZipEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith(RESOURCE_ROOT)) {
                    continue;
                }
                File dest = destinationFor(entry.getName(), geyserDir);
                if (dest == null) {
                    continue;
                }
                if (copyIfChanged(jar, entry, dest)) {
                    written++;
                }
            }
            if (written > 0) {
                logger.info("Installed/updated " + written + " Geyser custom-block asset file(s) in "
                        + geyserDir.getName() + "; a Geyser restart applies them on first install.");
            } else {
                logger.fine("Geyser custom-block assets already up to date.");
            }
        } catch (IOException e) {
            logger.warning("Could not install Geyser custom-block assets: " + e.getMessage()
                    + " (bombs will still render on Java; Bedrock cubes need these files).");
        }
    }

    /** Maps a bundled {@code geyser/…} entry to its destination under Geyser's folder, or null to skip. */
    private File destinationFor(String entryName, File geyserDir) {
        if (entryName.startsWith(MAPPINGS_PREFIX)) {
            String rel = entryName.substring(MAPPINGS_PREFIX.length());
            return new File(new File(geyserDir, "custom_mappings"), rel);
        }
        if (entryName.startsWith(PACK_PREFIX)) {
            String rel = entryName.substring(PACK_PREFIX.length());
            return new File(new File(new File(geyserDir, "packs"), PACK_FOLDER), rel);
        }
        return null;
    }

    /** Copies one entry to {@code dest} only if the content differs; returns whether it wrote. */
    private boolean copyIfChanged(ZipFile jar, ZipEntry entry, File dest) throws IOException {
        byte[] incoming;
        try (InputStream in = jar.getInputStream(entry)) {
            incoming = in.readAllBytes();
        }
        Path destPath = dest.toPath();
        if (dest.isFile()) {
            byte[] existing = Files.readAllBytes(destPath);
            if (Arrays.equals(existing, incoming)) {
                return false;
            }
        }
        Files.createDirectories(destPath.getParent());
        Files.write(destPath, incoming);
        return true;
    }

    /**
     * The Geyser plugin data folder to write into — an existing {@code Geyser-Spigot}/{@code Geyser}
     * directory, or, if only the Geyser JAR is present, the {@code Geyser-Spigot} folder to create.
     * Null when no Geyser is present at all.
     */
    private File locateGeyserFolder() {
        if (pluginsDir == null || !pluginsDir.isDirectory()) {
            return null;
        }
        for (String name : new String[] {"Geyser-Spigot", "Geyser"}) {
            File dir = new File(pluginsDir, name);
            if (dir.isDirectory()) {
                return dir;
            }
        }
        File[] jars = pluginsDir.listFiles(
                (dir, name) -> name.toLowerCase().matches("geyser.*\\.jar"));
        if (jars != null && jars.length > 0) {
            return new File(pluginsDir, "Geyser-Spigot"); // created on write
        }
        return null;
    }
}
