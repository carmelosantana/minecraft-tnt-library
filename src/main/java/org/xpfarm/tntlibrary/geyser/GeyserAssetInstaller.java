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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

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

    /**
     * File name the Bedrock pack is written as, under Geyser's {@code packs/}. Geyser only loads packs
     * that are {@code .zip}/{@code .mcpack} archive <em>files</em> — an unzipped directory in {@code
     * packs/} is silently ignored (geysermc.org/wiki/geyser/packs) — so the pack ships as one archive,
     * not the loose folder a prior version wrote.
     */
    private static final String PACK_MCPACK = "tnt_library.mcpack";

    /** The loose folder a prior version wrote (and Geyser ignored); removed on install if present. */
    private static final String LEGACY_PACK_FOLDER = "tnt_library";

    /**
     * A fixed modification time stamped on every archive entry (1980-01-01, the earliest a ZIP can
     * encode) so an unchanged pack always serialises to byte-identical output — that keeps the
     * "already up to date" comparison stable instead of rewriting the archive every startup.
     */
    private static final long STABLE_ENTRY_TIME = 315_532_800_000L;

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
            // The Bedrock pack must ship as one archive; collect its entries (sorted → deterministic
            // archive bytes) rather than writing them loose, and copy the mapping JSON(s) as files.
            Map<String, byte[]> packEntries = new TreeMap<>();
            Enumeration<? extends ZipEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(RESOURCE_ROOT)) {
                    continue;
                }
                if (name.startsWith(MAPPINGS_PREFIX)) {
                    File dest = new File(new File(geyserDir, "custom_mappings"),
                            name.substring(MAPPINGS_PREFIX.length()));
                    if (copyIfChanged(readAll(jar, entry), dest)) {
                        written++;
                    }
                } else if (name.startsWith(PACK_PREFIX)) {
                    // Key relative to geyser/pack/ so manifest.json lands at the archive root.
                    packEntries.put(name.substring(PACK_PREFIX.length()), readAll(jar, entry));
                }
            }
            if (writePackArchiveIfChanged(packEntries, geyserDir)) {
                written++;
            }
            removeLegacyPackFolder(geyserDir);
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

    /** Reads one JAR entry fully into a byte array. */
    private static byte[] readAll(ZipFile jar, ZipEntry entry) throws IOException {
        try (InputStream in = jar.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }

    /** Writes {@code incoming} to {@code dest} only if the content differs; returns whether it wrote. */
    private boolean copyIfChanged(byte[] incoming, File dest) throws IOException {
        Path destPath = dest.toPath();
        if (dest.isFile() && Arrays.equals(Files.readAllBytes(destPath), incoming)) {
            return false;
        }
        Files.createDirectories(destPath.getParent());
        Files.write(destPath, incoming);
        return true;
    }

    /**
     * Serialises the collected pack entries into a single {@code .mcpack} archive under Geyser's {@code
     * packs/} and writes it only when it differs from what is already there. Returns whether it wrote.
     */
    private boolean writePackArchiveIfChanged(Map<String, byte[]> packEntries, File geyserDir)
            throws IOException {
        if (packEntries.isEmpty()) {
            return false; // no bundled pack (nothing to serve) — leave Geyser's packs/ untouched
        }
        byte[] archive = buildArchive(packEntries);
        return copyIfChanged(archive, new File(new File(geyserDir, "packs"), PACK_MCPACK));
    }

    /** Builds a deterministic ZIP from {@code entries} (sorted, fixed timestamps) as {@code .mcpack}. */
    private static byte[] buildArchive(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(STABLE_ENTRY_TIME);
                zip.putNextEntry(zipEntry);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    /** Deletes the loose {@code packs/tnt_library/} folder a prior version wrote (Geyser ignored it). */
    private void removeLegacyPackFolder(File geyserDir) {
        File legacy = new File(new File(geyserDir, "packs"), LEGACY_PACK_FOLDER);
        if (!legacy.isDirectory()) {
            return;
        }
        try (Stream<Path> paths = Files.walk(legacy.toPath())) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort cleanup; a leftover loose file is harmless (Geyser never reads it)
                }
            });
            logger.info("Removed legacy unzipped Bedrock pack folder packs/" + LEGACY_PACK_FOLDER
                    + " — Geyser loads the " + PACK_MCPACK + " archive instead.");
        } catch (IOException e) {
            logger.fine("Could not remove legacy pack folder: " + e.getMessage());
        }
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
