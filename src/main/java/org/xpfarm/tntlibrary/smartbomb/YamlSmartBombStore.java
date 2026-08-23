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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Flat-file YAML {@link SmartBombStore} backed by an in-memory cache.
 *
 * <p>All queries ({@link #get}/{@link #put}/{@link #remove}/{@link #contains}/{@link #size}) hit the
 * cache only — never the disk — so the hot path stays allocation-cheap; persistence is an explicit
 * {@link #load()} / {@link #save()} the caller drives (an auto-load-on-construct and a save debounce
 * both belong to the later runtime task, not here).
 *
 * <p>The on-disk shape is deliberately dot-path-safe. {@link YamlConfiguration} treats {@code .} in a
 * path as a nesting separator, so a block key like {@code "world,x,y,z"} can never be a config path.
 * Instead a single {@code smartbombs} key holds a <em>list of strings</em>, each
 * {@code "<blockKey>=<serializedParams>"}. The block key never contains {@code =}, so a line splits on
 * its FIRST {@code =}: everything before is the {@link BlockKey}, everything after is the
 * {@link ParamCodec} value.
 *
 * <p>Loading is tolerant in the spirit of the plugin's config layer: a malformed line — an unparseable
 * key or a line with no {@code =} — is skipped silently rather than aborting the load, and a missing
 * file simply loads to an empty store.
 */
public final class YamlSmartBombStore implements SmartBombStore {

    /** The single top-level YAML key holding the list of {@code "key=value"} entry lines. */
    private static final String ROOT_KEY = "smartbombs";

    private static final Logger LOGGER = Logger.getLogger(YamlSmartBombStore.class.getName());

    private final File file;

    /** Insertion-ordered so a save/load round-trip preserves a stable, diff-friendly line order. */
    private final Map<BlockKey, SmartBombParams> cache = new LinkedHashMap<>();

    /** Wraps the backing file; does NOT auto-load, so the caller controls load timing. */
    public YamlSmartBombStore(File file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    @Override
    public Optional<SmartBombParams> get(BlockKey key) {
        return Optional.ofNullable(cache.get(key));
    }

    @Override
    public void put(BlockKey key, SmartBombParams params) {
        cache.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(params, "params"));
    }

    @Override
    public void remove(BlockKey key) {
        cache.remove(key);
    }

    @Override
    public boolean contains(BlockKey key) {
        return cache.containsKey(key);
    }

    @Override
    public int size() {
        return cache.size();
    }

    /**
     * Replaces the cache with the file's contents. Clears the cache first, so a missing file (or an
     * absent {@code smartbombs} key) leaves the store empty. Each malformed line is skipped silently.
     */
    public void load() {
        cache.clear();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String line : config.getStringList(ROOT_KEY)) {
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue; // No key/value separator: skip.
            }
            Optional<BlockKey> key = BlockKey.parse(line.substring(0, eq));
            if (key.isEmpty()) {
                continue; // Unparseable block key: skip.
            }
            cache.put(key.get(), ParamCodec.parse(line.substring(eq + 1)));
        }
    }

    /**
     * Writes the cache to the file as the {@code smartbombs} list, creating parent directories as
     * needed. An immediate, synchronous write; the debounce belongs to the runtime task.
     */
    public void save() {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        List<String> lines = new ArrayList<>(cache.size());
        for (Map.Entry<BlockKey, SmartBombParams> entry : cache.entrySet()) {
            lines.add(entry.getKey().format() + "=" + ParamCodec.serialize(entry.getValue()));
        }
        YamlConfiguration config = new YamlConfiguration();
        config.set(ROOT_KEY, lines);
        try {
            config.save(file);
        } catch (IOException failedWrite) {
            LOGGER.log(Level.WARNING, "Failed to save Smart Bomb store to " + file, failedWrite);
        }
    }
}
