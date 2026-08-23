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

/**
 * Immutable per-block Smart Bomb configuration.
 *
 * <p>Every field is normalized in the compact constructor so an out-of-range instance is
 * <em>unrepresentable</em>: callers (persistence, the command, a future UI) never have to re-validate
 * what they read back. Clamping in one place — rather than at each edit site — is the single source of
 * truth for the bounds documented in the field table, so the "1..8 radius / 1..72000 delay / ..."
 * contract cannot drift.
 *
 * <p>{@code timeTrigger} is a world time-of-day in ticks or {@code null} when the time trigger is off.
 * A non-null value wraps with {@link Math#floorMod(long, long)} against a 24000-tick day, so 24000
 * folds to 0 and negative offsets count back from the end of the day — the same wrapping Minecraft
 * applies to world time — keeping the stored value canonical.
 */
public record SmartBombParams(
        int radius, int delayTicks, Long timeTrigger, boolean proximity, int proximityRadius) {

    /** Smallest explosion power. */
    public static final int RADIUS_MIN = 1;

    /** Largest explosion power. */
    public static final int RADIUS_MAX = 8;

    /** Shortest base fuse, in ticks, measured from arming. */
    public static final int DELAY_MIN = 1;

    /** Longest base fuse, in ticks (one hour at 20 tps). */
    public static final int DELAY_MAX = 72000;

    /** Earliest world time-of-day, in ticks, a time trigger may fire at. */
    public static final long TIME_TRIGGER_MIN = 0;

    /** Latest world time-of-day, in ticks, a time trigger may fire at. */
    public static final long TIME_TRIGGER_MAX = 23999;

    /** Length of a Minecraft day, in ticks; the modulus the time trigger wraps against. */
    public static final long TICKS_PER_DAY = 24000;

    /** Smallest proximity scan radius. */
    public static final int PROXIMITY_RADIUS_MIN = 1;

    /** Largest proximity scan radius, bounded so per-tick scans stay cheap. */
    public static final int PROXIMITY_RADIUS_MAX = 16;

    /** Fallback baseline the codec restores for missing or garbage persisted input. */
    public static final SmartBombParams DEFAULT = new SmartBombParams(4, 100, null, false, 6);

    /** Normalizes each field so the resulting record always satisfies its documented bounds. */
    public SmartBombParams {
        radius = clamp(radius, RADIUS_MIN, RADIUS_MAX);
        delayTicks = clamp(delayTicks, DELAY_MIN, DELAY_MAX);
        proximityRadius = clamp(proximityRadius, PROXIMITY_RADIUS_MIN, PROXIMITY_RADIUS_MAX);
        timeTrigger = (timeTrigger == null) ? null : Math.floorMod(timeTrigger, TICKS_PER_DAY);
    }

    /** Returns a copy with a clamped {@code radius}, leaving this instance unchanged. */
    public SmartBombParams withRadius(int newRadius) {
        return new SmartBombParams(newRadius, delayTicks, timeTrigger, proximity, proximityRadius);
    }

    /** Returns a copy with a clamped {@code delayTicks}, leaving this instance unchanged. */
    public SmartBombParams withDelayTicks(int newDelayTicks) {
        return new SmartBombParams(radius, newDelayTicks, timeTrigger, proximity, proximityRadius);
    }

    /** Returns a copy with a wrapped {@code timeTrigger} ({@code null} = off), leaving this unchanged. */
    public SmartBombParams withTimeTrigger(Long newTimeTrigger) {
        return new SmartBombParams(radius, delayTicks, newTimeTrigger, proximity, proximityRadius);
    }

    /** Returns a copy with the given {@code proximity} flag, leaving this instance unchanged. */
    public SmartBombParams withProximity(boolean newProximity) {
        return new SmartBombParams(radius, delayTicks, timeTrigger, newProximity, proximityRadius);
    }

    /** Returns a copy with a clamped {@code proximityRadius}, leaving this instance unchanged. */
    public SmartBombParams withProximityRadius(int newProximityRadius) {
        return new SmartBombParams(radius, delayTicks, timeTrigger, proximity, newProximityRadius);
    }

    private static int clamp(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
