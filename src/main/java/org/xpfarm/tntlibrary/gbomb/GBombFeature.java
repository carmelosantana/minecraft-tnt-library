/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.gbomb;

import java.util.Objects;
import org.bukkit.event.HandlerList;
import org.xpfarm.tntlibrary.TntLibraryPlugin;
import org.xpfarm.tntlibrary.config.TntLibraryConfig;

/**
 * The G-Bomb feature module: it owns the one {@link GBombRuntime} — the shared gravity ledger, the set
 * of in-flight sequence tasks, and the clamped, config-derived {@link GBombParams} — plus the {@link
 * GBomb} bound to it, so the plugin bootstrap can wire the whole bomb in a couple of lines rather than
 * constructing the collaborators inline. Analogous to {@code smartbomb/SmartBombFeature}.
 *
 * <p>The runtime's params come from two sources folded together in the constructor, mirroring the
 * Smart Bomb precedent: {@code radius}/{@code hangTicks} from the bomb's own {@link
 * org.xpfarm.tntlibrary.config.BombSettings} (the same config the generic detonation path reads), and
 * {@code launchPower}/{@code killDamage} from {@link GBombDefaults} under {@code bombs.gbomb.}.
 *
 * <h2>#1 review gate — gravity restore on every path</h2>
 *
 * <p>This module carries two of the mandatory restore paths. {@link #enable()} registers a {@link
 * GravityRestoreListener} that restores any tracked entity the instant it unloads mid-sequence (the
 * {@code NoGravity} NBT flag persists across unload, so this listener is not optional). {@link
 * #disable()} calls {@link GBombRuntime#cancelAll()}, which cancels every in-flight sequence <em>and</em>
 * restores gravity for every still-tracked entity via {@link GBombRuntime#restoreAll()} — Bukkit cancels
 * a plugin's tasks on disable but does not restore gravity, so the disable path must. Together with the
 * per-slam restore in the sequence task, these close the gate for the plugin-disable and chunk/entity
 * -unload cases.
 *
 * <p>Everything here needs a live server (the runtime schedules Bukkit tasks, the listener consumes a
 * Bukkit event), so this module is runtime-only and verified at the runtime gate (gate 12) rather than
 * in JUnit — gate-12-verified.
 */
public final class GBombFeature {

    private final TntLibraryPlugin plugin;
    private final GBombRuntime runtime;
    private final GBomb gbomb;

    /** The registered unload safety-net listener; null until {@link #enable()}, cleared on {@link #disable()}. */
    private GravityRestoreListener listener;

    /**
     * Builds the G-Bomb service graph. {@code radius}/{@code hangTicks} for the runtime params come from
     * the bomb's {@link org.xpfarm.tntlibrary.config.BombSettings}; {@code launchPower}/{@code killDamage}
     * come from {@link GBombDefaults} under {@code bombs.gbomb.}. The fuse length comes from the same
     * {@code BombSettings}.
     *
     * @param plugin the owning plugin; never {@code null}
     * @param config the current validated config snapshot; never {@code null}
     */
    public GBombFeature(TntLibraryPlugin plugin, TntLibraryConfig config) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        this.plugin = plugin;
        GBombDefaults defaults = GBombDefaults.from(plugin.getConfig(), plugin.getLogger());
        GBombParams params = defaults.params(config.bomb(GBomb.ID));
        this.runtime = new GBombRuntime(plugin, params);
        this.gbomb = new GBomb(runtime, config.bomb(GBomb.ID).fuseTicks());
    }

    /** Registers the mandatory gravity-restore unload listener. Call once on enable. */
    public void enable() {
        this.listener = new GravityRestoreListener(runtime);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    /**
     * Cancels every in-flight sequence and restores gravity for every still-tracked entity via {@link
     * GBombRuntime#cancelAll()}, then unregisters the listener. Part of the #1 gravity-restore gate: the
     * disable path must restore mid-sequence entities because Bukkit cancels tasks on disable without
     * restoring gravity. Unregistering the listener when non-null keeps a reload's rebuild from
     * double-registering — exactly like {@code SmartBombFeature}. Call on disable.
     */
    public void disable() {
        runtime.cancelAll();
        if (listener != null) {
            HandlerList.unregisterAll(listener); // so a reload's rebuild doesn't double-register
        }
    }

    /** The configured G-Bomb, for the orchestrator to register in {@code TntRegistry} / the plugin. */
    public GBomb gbomb() {
        return gbomb;
    }
}
