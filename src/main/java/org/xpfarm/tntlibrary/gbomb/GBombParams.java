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

/**
 * Immutable G-Bomb configuration, injected once from config and consumed by the whole G-Bomb feature.
 *
 * <p>Every field is normalized in the compact constructor so an out-of-range instance is
 * <em>unrepresentable</em>: callers never have to re-validate what they read back. Clamping in one
 * place — rather than at each use site — is the single source of truth for the bounds documented in
 * the field table, so the "1..64 radius / 1..200 hang / 0..3 launch / 1..1e6 kill" contract cannot
 * drift.
 *
 * <p>Unlike {@code SmartBombParams}, G-Bomb params are built once from config and never edited
 * per-block, so there are deliberately no {@code with*} copy methods.
 *
 * <p>{@code launchPower} is capped at 3.0 to keep launch magnitudes modest — a guard against the
 * Paper {@code setVelocity} Y-axis regression (#13270).
 *
 * @param radius the effect radius, in blocks
 * @param hangTicks how long targets hang at apex, in ticks, before the slam
 * @param launchPower the upward launch magnitude
 * @param killDamage the server-side FALL finisher damage applied on the slam
 */
public record GBombParams(int radius, int hangTicks, double launchPower, double killDamage) {

    /** Smallest effect radius, in blocks. */
    public static final int RADIUS_MIN = 1;

    /** Largest effect radius, in blocks. */
    public static final int RADIUS_MAX = 64;

    /** Shortest apex hang, in ticks. */
    public static final int HANG_TICKS_MIN = 1;

    /** Longest apex hang, in ticks. */
    public static final int HANG_TICKS_MAX = 200;

    /** Smallest launch magnitude (no launch). */
    public static final double LAUNCH_POWER_MIN = 0.0;

    /** Largest launch magnitude, capped for the Paper #13270 setVelocity regression. */
    public static final double LAUNCH_POWER_MAX = 3.0;

    /** Smallest FALL finisher damage. */
    public static final double KILL_DAMAGE_MIN = 1.0;

    /** Largest FALL finisher damage. */
    public static final double KILL_DAMAGE_MAX = 1_000_000.0;

    /** Baseline restored for missing or garbage config input. */
    public static final GBombParams DEFAULT = new GBombParams(20, 50, 1.2, 1000.0);

    /** Normalizes each field so the resulting record always satisfies its documented bounds. */
    public GBombParams {
        radius = clampInt(radius, RADIUS_MIN, RADIUS_MAX);
        hangTicks = clampInt(hangTicks, HANG_TICKS_MIN, HANG_TICKS_MAX);
        launchPower = clampDouble(launchPower, LAUNCH_POWER_MIN, LAUNCH_POWER_MAX);
        killDamage = clampDouble(killDamage, KILL_DAMAGE_MIN, KILL_DAMAGE_MAX);
    }

    private static int clampInt(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    private static double clampDouble(double value, double lo, double hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
