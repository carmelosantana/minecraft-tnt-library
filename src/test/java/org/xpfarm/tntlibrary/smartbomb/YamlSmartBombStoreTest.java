/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.smartbomb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the {@link YamlSmartBombStore} contract: the cache answers put/get/remove/contains/size with no
 * I/O, a save/load round-trip preserves every entry byte-exactly, a missing file loads empty, and a
 * malformed persisted line is skipped rather than crashing the load.
 */
final class YamlSmartBombStoreTest {

    private static final BlockKey KEY_A = new BlockKey("world", 1, 2, 3);
    private static final BlockKey KEY_B = new BlockKey("world_nether", -10, 64, 200);

    @Test
    void cacheHandlesPutGetContainsRemoveSize(@TempDir Path dir) {
        YamlSmartBombStore store = new YamlSmartBombStore(dir.resolve("smart.yml").toFile());

        assertEquals(0, store.size());
        assertTrue(store.get(KEY_A).isEmpty());
        assertFalse(store.contains(KEY_A));

        SmartBombParams params = new SmartBombParams(6, 200, 6000L, true, 10);
        store.put(KEY_A, params);

        assertEquals(1, store.size());
        assertTrue(store.contains(KEY_A));
        assertEquals(Optional.of(params), store.get(KEY_A));

        SmartBombParams replacement = new SmartBombParams(1, 1, null, false, 1);
        store.put(KEY_A, replacement);
        assertEquals(1, store.size());
        assertEquals(Optional.of(replacement), store.get(KEY_A));

        store.remove(KEY_A);
        assertEquals(0, store.size());
        assertFalse(store.contains(KEY_A));
    }

    @Test
    void saveThenLoadRoundTripsEveryEntry(@TempDir Path dir) {
        File file = dir.resolve("smart.yml").toFile();
        YamlSmartBombStore writer = new YamlSmartBombStore(file);

        SmartBombParams withTime = new SmartBombParams(8, 72000, 23999L, true, 16);
        SmartBombParams nullTimeProximityOn = new SmartBombParams(4, 100, null, true, 6);
        writer.put(KEY_A, withTime);
        writer.put(KEY_B, nullTimeProximityOn);
        writer.save();

        YamlSmartBombStore reader = new YamlSmartBombStore(file);
        reader.load();

        assertEquals(2, reader.size());
        assertEquals(Optional.of(withTime), reader.get(KEY_A));
        assertEquals(Optional.of(nullTimeProximityOn), reader.get(KEY_B));
    }

    @Test
    void loadOnMissingFileYieldsEmptyStore(@TempDir Path dir) {
        YamlSmartBombStore store = new YamlSmartBombStore(dir.resolve("does-not-exist.yml").toFile());
        store.load();
        assertEquals(0, store.size());
    }

    @Test
    void loadSkipsMalformedLinesAndKeepsGoodOnes(@TempDir Path dir) throws Exception {
        File file = dir.resolve("smart.yml").toFile();
        SmartBombParams good = new SmartBombParams(5, 150, 8000L, false, 7);

        YamlConfiguration handWritten = new YamlConfiguration();
        handWritten.set(
                "smartbombs",
                List.of(
                        KEY_A.format() + "=" + ParamCodec.serialize(good),
                        "not-a-valid-line-without-equals",
                        "bad,key=radius=4,delay=100,time=,proximity=false,proximity-radius=6"));
        handWritten.save(file);

        YamlSmartBombStore store = new YamlSmartBombStore(file);
        store.load();

        assertEquals(1, store.size());
        assertEquals(Optional.of(good), store.get(KEY_A));
    }

    @Test
    void saveThenLoadOfEmptyStoreYieldsEmpty(@TempDir Path dir) {
        File file = dir.resolve("smart.yml").toFile();
        YamlSmartBombStore writer = new YamlSmartBombStore(file);
        writer.save();

        YamlSmartBombStore reader = new YamlSmartBombStore(file);
        reader.load();
        assertEquals(0, reader.size());
    }

    @Test
    void saveCreatesMissingParentDirectories(@TempDir Path dir) {
        File file = dir.resolve("nested/deeper/smart.yml").toFile();
        YamlSmartBombStore store = new YamlSmartBombStore(file);
        store.put(KEY_A, SmartBombParams.DEFAULT);
        store.save();

        assertTrue(file.exists());
    }

    @Test
    void loadReplacesPriorCacheContents(@TempDir Path dir) throws Exception {
        File file = dir.resolve("smart.yml").toFile();
        Files.writeString(dir.resolve("smart.yml"), "smartbombs: []\n");

        YamlSmartBombStore store = new YamlSmartBombStore(file);
        store.put(KEY_B, SmartBombParams.DEFAULT);
        assertEquals(1, store.size());

        store.load();
        assertEquals(0, store.size());
    }
}
