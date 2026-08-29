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

/**
 * Immutable White Out configuration, injected once from config and consumed by the whole feature.
 *
 * <p>Every field is normalized in the compact constructor so an out-of-range instance is
 * <em>unrepresentable</em>: callers never re-validate what they read back. {@code pullPower} is capped
 * at 3.0 to keep pull magnitudes modest — a guard against the Paper {@code setVelocity} Y-axis
 * regression (#13270), mirroring {@code GBombParams.launchPower}.
 *
 * @param radius the pull/scar radius, in blocks (the bomb's {@code pull-radius})
 * @param pullPower the escalating inward per-tick velocity ceiling
 * @param pullTicks the length of the active pull/blind window before collapse, in ticks
 * @param killDamage the collapse FREEZE finisher damage
 * @param effectTicks how long blindness/slowness/freeze lingers on victims, in ticks
 */
public record WhiteoutParams(
        int radius, double pullPower, int pullTicks, double killDamage, int effectTicks) {

    /** Smallest pull/scar radius, in blocks. */
    public static final int RADIUS_MIN = 1;
    /** Largest pull/scar radius, in blocks. */
    public static final int RADIUS_MAX = 64;

    /** Smallest inward pull magnitude (no pull). */
    public static final double PULL_POWER_MIN = 0.0;
    /** Largest inward pull magnitude, capped for the Paper #13270 setVelocity regression. */
    public static final double PULL_POWER_MAX = 3.0;

    /** Shortest active pull window, in ticks. */
    public static final int PULL_TICKS_MIN = 1;
    /** Longest active pull window, in ticks. */
    public static final int PULL_TICKS_MAX = 1200;

    /** Smallest FREEZE finisher damage. */
    public static final double KILL_DAMAGE_MIN = 1.0;
    /** Largest FREEZE finisher damage. */
    public static final double KILL_DAMAGE_MAX = 1_000_000.0;

    /** Shortest debuff linger, in ticks. */
    public static final int EFFECT_TICKS_MIN = 1;
    /** Longest debuff linger, in ticks. */
    public static final int EFFECT_TICKS_MAX = 1200;

    /** Baseline restored for missing or garbage config input. */
    public static final WhiteoutParams DEFAULT = new WhiteoutParams(24, 1.0, 60, 1000.0, 100);

    /** Normalizes each field so the resulting record always satisfies its documented bounds. */
    public WhiteoutParams {
        radius = clampInt(radius, RADIUS_MIN, RADIUS_MAX);
        pullPower = clampDouble(pullPower, PULL_POWER_MIN, PULL_POWER_MAX);
        pullTicks = clampInt(pullTicks, PULL_TICKS_MIN, PULL_TICKS_MAX);
        killDamage = clampDouble(killDamage, KILL_DAMAGE_MIN, KILL_DAMAGE_MAX);
        effectTicks = clampInt(effectTicks, EFFECT_TICKS_MIN, EFFECT_TICKS_MAX);
    }

    private static int clampInt(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    private static double clampDouble(double value, double lo, double hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
