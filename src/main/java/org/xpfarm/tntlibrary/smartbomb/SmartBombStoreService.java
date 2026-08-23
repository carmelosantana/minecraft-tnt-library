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
import java.util.Objects;
import java.util.Optional;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * The persistence lifecycle around a {@link YamlSmartBombStore}: load on enable, debounced saves during
 * play, and a guaranteed flush on disable.
 *
 * <h2>Debounce rationale</h2>
 *
 * <p>A player tweaking a Smart Bomb through the chest-GUI (Task 6) produces a click storm — many
 * {@link #put}/{@link #remove} edits in a few seconds. Writing the whole store to disk on every edit
 * would hammer the disk for no benefit, so an edit does not save immediately: it (re)schedules a single
 * save {@value #DEBOUNCE_TICKS} ticks (5 s) out, cancelling any save still pending. A burst of edits
 * therefore collapses to exactly one write, 5 s after the <em>last</em> edit.
 *
 * <h2>Disable-time flush guarantee</h2>
 *
 * <p>Because a save can be pending when the server stops, {@link #flush()} cancels the pending task and
 * writes synchronously right then. Calling it from the plugin's {@code onDisable} guarantees no
 * programmed edit is ever lost to a shutdown that beat the debounce timer.
 *
 * <h2>Threading</h2>
 *
 * <p>Every caller is a Bukkit event or scheduled task, so all access is on the server main thread and
 * this class holds no synchronisation — mirroring {@code BombFuse}'s main-thread-only contract.
 * Server-dependent (uses the Bukkit scheduler); verified at the runtime gate, not in JUnit.
 */
public final class SmartBombStoreService {

    /** Debounce window, in ticks (5 s at 20 tps): a save fires this long after the last edit. */
    public static final long DEBOUNCE_TICKS = 100L;

    private final Plugin plugin;
    private final YamlSmartBombStore store;

    /** The pending debounced save, or {@code null} when none is scheduled. Main-thread only. */
    private BukkitTask pending;

    /** Wraps a fresh {@link YamlSmartBombStore} over {@code file}. */
    public SmartBombStoreService(Plugin plugin, File file) {
        this(plugin, new YamlSmartBombStore(file));
    }

    /** Wraps a caller-supplied {@link YamlSmartBombStore} (flexibility/testing seam). */
    public SmartBombStoreService(Plugin plugin, YamlSmartBombStore store) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.store = Objects.requireNonNull(store, "store");
    }

    /** Loads the backing store from disk. Call once on enable. */
    public void load() {
        store.load();
    }

    /** Returns the params stored for {@code key}, or {@link Optional#empty()} if none. */
    public Optional<SmartBombParams> get(BlockKey key) {
        return store.get(key);
    }

    /** Stores {@code params} for {@code key} and schedules a debounced save. */
    public void put(BlockKey key, SmartBombParams params) {
        store.put(key, params);
        scheduleSave();
    }

    /** Removes any params for {@code key} and schedules a debounced save. */
    public void remove(BlockKey key) {
        store.remove(key);
        scheduleSave();
    }

    /** Returns whether {@code key} currently has stored params. */
    public boolean contains(BlockKey key) {
        return store.contains(key);
    }

    /**
     * Cancels any pending debounced save and writes the store to disk immediately. Call on disable so a
     * shutdown that beats the debounce timer never loses a pending edit.
     */
    public void flush() {
        if (pending != null) {
            pending.cancel();
            pending = null;
        }
        store.save();
    }

    /**
     * (Re)arms the debounce: cancels any save still pending and schedules a fresh one {@value
     * #DEBOUNCE_TICKS} ticks out, so a burst of edits collapses to one write after the last edit.
     */
    private void scheduleSave() {
        if (pending != null) {
            pending.cancel();
        }
        pending = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            store.save();
            pending = null;
        }, DEBOUNCE_TICKS);
    }
}
