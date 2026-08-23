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
import org.bukkit.event.HandlerList;
import org.xpfarm.tntlibrary.TntLibraryPlugin;
import org.xpfarm.tntlibrary.config.TntLibraryConfig;
import org.xpfarm.tntlibrary.delivery.BedrockDetector;

/**
 * The Smart Bomb feature module: it owns every Smart Bomb runtime service — the store, the
 * programmer, the watcher, and the seeded placement defaults — so the plugin bootstrap can wire the
 * whole bomb in a couple of lines rather than constructing four collaborators inline.
 *
 * <p>The {@code seedParams} field is the {@link SmartBombParams} a freshly placed Smart Bomb starts
 * with: its explosion radius and base delay come from the bomb's own {@code BombSettings} (the same
 * config the generic detonation path reads), while its proximity behaviour comes from {@link
 * SmartBombDefaults} under {@code bombs.smartbomb.}. The programmer falls back to this seed when a
 * placed block has no stored entry yet.
 *
 * <p>Everything here needs a live server (the store schedules Bukkit tasks, the watcher runs
 * scheduled loops), so this module is runtime-only and verified at the runtime gate rather than in
 * JUnit — gate-12-verified.
 */
public final class SmartBombFeature {

    private final TntLibraryPlugin plugin;
    private final SmartBombStoreService store;
    private final SmartBombWatcher watcher;
    private final SmartBombProgrammer programmer;
    private final SmartBombParams seedParams;
    private final BedrockDetector detector;

    /** The registered interaction listener; null until {@link #enable()}, cleared on {@link #disable()}. */
    private SmartBombListener listener;

    /**
     * Builds the Smart Bomb service graph. {@code radius}/{@code delay} for the placement seed come
     * from the bomb's {@link org.xpfarm.tntlibrary.config.BombSettings}; the proximity defaults come
     * from {@link SmartBombDefaults}.
     *
     * @param plugin the owning plugin; never {@code null}
     * @param config the current validated config snapshot; never {@code null}
     */
    public SmartBombFeature(TntLibraryPlugin plugin, TntLibraryConfig config) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        this.plugin = plugin;
        this.store = new SmartBombStoreService(plugin, new File(plugin.getDataFolder(), "smartbombs.yml"));
        this.watcher = new SmartBombWatcher(plugin, store);
        this.programmer = new SmartBombProgrammer(store);
        SmartBombDefaults defaults = SmartBombDefaults.from(plugin.getConfig(), plugin.getLogger());
        this.seedParams = defaults.seed(
                config.bomb(SmartBomb.ID).radius(), config.bomb(SmartBomb.ID).fuseTicks());
        this.detector = BedrockDetector.create(plugin.getLogger());
    }

    /** Loads the store from disk and registers the interaction listener. Call once on enable. */
    public void enable() {
        store.load();
        this.listener = new SmartBombListener(plugin, this, detector);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    /** Unregisters the listener, cancels every armed watcher, and flushes the store. Call on disable. */
    public void disable() {
        watcher.cancelAll();
        store.flush();
        if (listener != null) {
            HandlerList.unregisterAll(listener); // so a reload's rebuild doesn't double-register
        }
    }

    /** The single DRY write/read path for a placed Smart Bomb's programming. */
    public SmartBombProgrammer programmer() {
        return programmer;
    }

    /** The per-armed-block trigger loop that arms and detonates Smart Bombs. */
    public SmartBombWatcher watcher() {
        return watcher;
    }

    /** The persistence lifecycle around the Smart Bomb store. */
    public SmartBombStoreService store() {
        return store;
    }

    /** The {@link SmartBombParams} a freshly placed Smart Bomb starts with (the programmer fallback). */
    public SmartBombParams seedParams() {
        return seedParams;
    }
}
