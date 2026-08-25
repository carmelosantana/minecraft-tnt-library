/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.fbomb;

import java.util.Objects;
import org.xpfarm.tntlibrary.TntLibraryPlugin;
import org.xpfarm.tntlibrary.config.TntLibraryConfig;

/**
 * The F-Bomb feature module: it owns the {@link FBombDirector} — the single-task cinematic engine
 * over every live F-Bomb show — so the plugin bootstrap can wire the whole bomb in a couple of lines
 * rather than constructing the director and its seeded defaults inline. Mirrors {@code
 * SmartBombFeature}.
 *
 * <p>The orchestrator constructs this in bootstrap, exposes it via a {@code plugin.fBomb()}
 * accessor, and diverts a placed F-Bomb's ignition to {@code director().summon(block, igniter)} from
 * the shared ignition listener; none of that wiring lives here.
 *
 * <p>Everything here needs a live server (the director schedules Bukkit tasks and spawns entities),
 * so this module is runtime-only and verified at the runtime gate rather than in JUnit —
 * gate-12-verified.
 */
public final class FBombFeature {

    private final FBombDirector director;

    /**
     * Builds the F-Bomb service graph: reads the F-Bomb's extra defaults from {@code
     * bombs.fbomb.} and constructs the {@link FBombDirector}.
     *
     * @param plugin the owning plugin; never {@code null}
     * @param config the current validated config snapshot; never {@code null}
     */
    public FBombFeature(TntLibraryPlugin plugin, TntLibraryConfig config) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        FBombDefaults defaults = FBombDefaults.from(plugin.getConfig(), plugin.getLogger());
        this.director = new FBombDirector(plugin, config, defaults);
    }

    /** Sweeps any crash-orphaned rig entities, then starts the single global tick task. */
    public void enable() {
        director.cleanupOrphans();
        director.start();
    }

    /** Cancels the tick task and tears down every live show. Call on disable. */
    public void disable() {
        director.shutdown();
    }

    /** The cinematic engine; the ignition-divert entry point ({@code summon}) lives here. */
    public FBombDirector director() {
        return director;
    }
}
