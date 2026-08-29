/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.whiteout;

import java.util.Objects;
import org.bukkit.event.HandlerList;
import org.xpfarm.tntlibrary.TntLibraryPlugin;
import org.xpfarm.tntlibrary.config.TntLibraryConfig;

/**
 * The White Out feature module: it owns the one {@link WhiteoutRuntime} — the shared effect ledger, the
 * set of in-flight sequence tasks, and the clamped, config-derived {@link WhiteoutParams} — plus the
 * {@link WhiteOut} bound to it, so the plugin bootstrap can wire the whole bomb in a couple of lines.
 * Analogous to {@code gbomb/GBombFeature}.
 *
 * <p>The runtime's params come from two sources folded together in the constructor: {@code radius} and
 * the fuse from the bomb's own {@link org.xpfarm.tntlibrary.config.BombSettings}, and
 * {@code pullPower}/{@code pullTicks}/{@code killDamage}/{@code effectTicks} from {@link WhiteoutDefaults}
 * under {@code bombs.whiteout.}.
 *
 * <h2>No-leaked-state gate</h2>
 *
 * <p>{@link #enable()} registers the {@link WhiteoutCleanupListener} that clears this bomb's effects the
 * instant a caught entity unloads mid-storm. {@link #disable()} calls {@link WhiteoutRuntime#cancelAll()},
 * which cancels every in-flight sequence and clears the debuffs for every still-tracked entity —
 * Bukkit cancels a plugin's tasks on disable but does not clear potion/freeze state, so the disable path
 * must. Server-dependent; verified at the runtime gate.
 */
public final class WhiteoutFeature {

    private final TntLibraryPlugin plugin;
    private final WhiteoutRuntime runtime;
    private final WhiteOut whiteout;

    /** The registered cleanup listener; null until {@link #enable()}, cleared on {@link #disable()}. */
    private WhiteoutCleanupListener listener;

    public WhiteoutFeature(TntLibraryPlugin plugin, TntLibraryConfig config) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        this.plugin = plugin;
        WhiteoutDefaults defaults = WhiteoutDefaults.from(plugin.getConfig(), plugin.getLogger());
        WhiteoutParams params = defaults.params(config.bomb(WhiteOut.ID));
        this.runtime = new WhiteoutRuntime(plugin, params);
        this.whiteout = new WhiteOut(runtime, config.bomb(WhiteOut.ID).fuseTicks());
    }

    /** Registers the cleanup unload listener. Call once on enable. */
    public void enable() {
        this.listener = new WhiteoutCleanupListener(runtime);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    /**
     * Cancels every in-flight sequence and clears effects for every still-tracked entity via
     * {@link WhiteoutRuntime#cancelAll()}, then unregisters the listener. Unregistering when non-null
     * keeps a reload's rebuild from double-registering — exactly like {@code GBombFeature}. Call on
     * disable.
     */
    public void disable() {
        runtime.cancelAll();
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
    }

    /** The configured White Out, for the orchestrator to register in {@code TntRegistry} / the plugin. */
    public WhiteOut whiteout() {
        return whiteout;
    }
}
