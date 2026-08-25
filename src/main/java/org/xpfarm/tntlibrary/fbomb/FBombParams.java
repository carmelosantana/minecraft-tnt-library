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

/**
 * Immutable, injected configuration for a single F-Bomb.
 *
 * <p>Every field is normalized in the compact constructor so an out-of-range instance is
 * <em>unrepresentable</em>: callers never have to re-validate what they read back. Clamping in one
 * place — rather than at each edit site — is the single source of truth for the bounds documented
 * in the field table below, so the contract cannot drift.
 *
 * <p>{@code radius} and {@code fuseTicks} arrive from {@code BombSettings} (the shared per-block
 * settings every bomb type reads); the remaining fields arrive from {@code FBombDefaults} (the
 * F-Bomb-specific defaults).
 */
public record FBombParams(
        int radius,
        int fuseTicks,
        int menaceTicks,
        int spawnDistance,
        int spawnHeight,
        int bossbarRange,
        int skullCount,
        int skullCadenceTicks) {

    /** Smallest explosion power. */
    public static final int RADIUS_MIN = 1;

    /** Largest explosion power. */
    public static final int RADIUS_MAX = 8;

    /** Shortest base fuse, in ticks, measured from arming. */
    public static final int FUSE_TICKS_MIN = 1;

    /** Longest base fuse, in ticks (one minute at 20 tps). */
    public static final int FUSE_TICKS_MAX = 1200;

    /** Shortest menace phase, in ticks, before detonation. */
    public static final int MENACE_TICKS_MIN = 20;

    /** Longest menace phase, in ticks (one minute at 20 tps). */
    public static final int MENACE_TICKS_MAX = 1200;

    /** Closest distance, in blocks, a skull may spawn from the F-Bomb. */
    public static final int SPAWN_DISTANCE_MIN = 2;

    /** Farthest distance, in blocks, a skull may spawn from the F-Bomb. */
    public static final int SPAWN_DISTANCE_MAX = 32;

    /** Lowest vertical offset, in blocks, a skull may spawn at. */
    public static final int SPAWN_HEIGHT_MIN = 0;

    /** Highest vertical offset, in blocks, a skull may spawn at. */
    public static final int SPAWN_HEIGHT_MAX = 32;

    /** Smallest boss bar visibility range, in blocks. */
    public static final int BOSSBAR_RANGE_MIN = 8;

    /** Largest boss bar visibility range, in blocks. */
    public static final int BOSSBAR_RANGE_MAX = 256;

    /** Fewest skulls an F-Bomb may spawn. */
    public static final int SKULL_COUNT_MIN = 0;

    /** Most skulls an F-Bomb may spawn. */
    public static final int SKULL_COUNT_MAX = 32;

    /** Shortest interval, in ticks, between skull spawns. */
    public static final int SKULL_CADENCE_TICKS_MIN = 1;

    /** Longest interval, in ticks, between skull spawns. */
    public static final int SKULL_CADENCE_TICKS_MAX = 200;

    /** Fallback baseline the codec restores for missing or garbage persisted input. */
    public static final FBombParams DEFAULT = new FBombParams(6, 60, 60, 12, 6, 48, 6, 8);

    /** Normalizes each field so the resulting record always satisfies its documented bounds. */
    public FBombParams {
        radius = clamp(radius, RADIUS_MIN, RADIUS_MAX);
        fuseTicks = clamp(fuseTicks, FUSE_TICKS_MIN, FUSE_TICKS_MAX);
        menaceTicks = clamp(menaceTicks, MENACE_TICKS_MIN, MENACE_TICKS_MAX);
        spawnDistance = clamp(spawnDistance, SPAWN_DISTANCE_MIN, SPAWN_DISTANCE_MAX);
        spawnHeight = clamp(spawnHeight, SPAWN_HEIGHT_MIN, SPAWN_HEIGHT_MAX);
        bossbarRange = clamp(bossbarRange, BOSSBAR_RANGE_MIN, BOSSBAR_RANGE_MAX);
        skullCount = clamp(skullCount, SKULL_COUNT_MIN, SKULL_COUNT_MAX);
        skullCadenceTicks = clamp(skullCadenceTicks, SKULL_CADENCE_TICKS_MIN, SKULL_CADENCE_TICKS_MAX);
    }

    private static int clamp(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
