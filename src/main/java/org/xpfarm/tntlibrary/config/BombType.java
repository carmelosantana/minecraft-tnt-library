/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.config;

import org.xpfarm.tntlibrary.twins.TwinColor;

/**
 * The catalogue of known bombs and the exact {@code config.yml} keys, defaults, and shape of each.
 *
 * <p>This is the single source of truth that maps each bomb's own key vocabulary onto the neutral
 * fields of {@link BombSettings}. It exists so {@link TntLibraryConfig} can parse every bomb with
 * one loop instead of six hand-written blocks, and so the wiring layer can enumerate the known bomb
 * ids ({@link #id()}) without hard-coding a list that could drift from what actually parses.
 *
 * <p>Every value here mirrors {@code src/main/resources/config.yml} exactly; {@code
 * TntLibraryConfigTest.theShippedConfigYmlMatchesTheseDefaults()} pins that agreement so the two can
 * never silently diverge.
 */
public enum BombType {
    WATERBOMB("waterbomb", true, "radius", 4, "fuse-ticks", 80, null, 0),
    TWINS("twins", true, "radius", 3, "fuse-ticks", 80, "max-pair-distance", 64),
    SMARTBOMB("smartbomb", true, "default-radius", 4, "default-delay-ticks", 100, null, 0),
    FBOMB("fbomb", false, "radius", 6, "fuse-ticks", 60, null, 0),
    GBOMB("gbomb", false, "radius", 20, "fuse-ticks", 60, "hang-ticks", 50),
    WHITEOUT("whiteout", false, "pull-radius", 24, "fuse-ticks", 100, null, 0);

    private final String id;
    private final boolean defaultEnabled;
    private final String radiusKey;
    private final int radiusDefault;
    private final String fuseKey;
    private final int fuseDefault;
    private final String hangKey;
    private final int hangDefault;

    BombType(
            String id,
            boolean defaultEnabled,
            String radiusKey,
            int radiusDefault,
            String fuseKey,
            int fuseDefault,
            String hangKey,
            int hangDefault) {
        this.id = id;
        this.defaultEnabled = defaultEnabled;
        this.radiusKey = radiusKey;
        this.radiusDefault = radiusDefault;
        this.fuseKey = fuseKey;
        this.fuseDefault = fuseDefault;
        this.hangKey = hangKey;
        this.hangDefault = hangDefault;
    }

    /** The bomb's config-section id under {@code bombs.}, e.g. {@code waterbomb}. */
    public String id() {
        return id;
    }

    /** Whether this bomb ships enabled; dangerous bombs default to {@code false}. */
    public boolean defaultEnabled() {
        return defaultEnabled;
    }

    /** The radius key this bomb uses ({@code radius}/{@code pull-radius}/{@code default-radius}), or
     *  {@code null} for a bomb with no radius concept (the F-Bomb). */
    public String radiusKey() {
        return radiusKey;
    }

    /** The shipped default for {@link #radiusKey()}; {@code 0} when there is no radius key. */
    public int radiusDefault() {
        return radiusDefault;
    }

    /** The timing key this bomb uses ({@code fuse-ticks} or {@code default-delay-ticks}). */
    public String fuseKey() {
        return fuseKey;
    }

    /** The shipped default for {@link #fuseKey()}. */
    public int fuseDefault() {
        return fuseDefault;
    }

    /** The hang key this bomb uses ({@code hang-ticks}), or {@code null} for a bomb with no hang
     *  phase (every bomb except the G-Bomb). */
    public String hangKey() {
        return hangKey;
    }

    /** The shipped default for {@link #hangKey()}; {@code 0} when there is no hang key. */
    public int hangDefault() {
        return hangDefault;
    }

    /** The shipped defaults for this bomb as a {@link BombSettings}, ignoring the master switch. */
    public BombSettings defaults() {
        return new BombSettings(
                defaultEnabled,
                radiusKey == null ? 0 : radiusDefault,
                fuseDefault,
                hangKey == null ? 0 : hangDefault);
    }

    /**
     * The base config/permission id for a possibly-variant bomb id — the canonical resolver the
     * wiring layer uses so a variant id (e.g. {@code twins_white}/{@code twins_black}) reads the
     * shared {@code bombs.twins} config section and the shared {@code tntlibrary.use.twins}
     * permission node. A non-variant id is returned unchanged. The Twins are the only bomb that ships
     * as variants today, so this delegates to {@link TwinColor#baseId(String)}, the single source of
     * that mapping; a future variant bomb registers its own mapping there and inherits this resolver.
     *
     * @param id any bomb id, variant or not
     * @return the base id whose {@code bombs.<id>} section and {@code tntlibrary.use.<id>} node govern it
     */
    public static String baseId(String id) {
        return TwinColor.baseId(id);
    }
}
