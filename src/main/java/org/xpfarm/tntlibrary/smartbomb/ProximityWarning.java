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
 * Pure distance-to-warning mapping for a proximity-armed Smart Bomb.
 *
 * <p>The warning "intensifies as the entity approaches": as the nearest entity closes from the scan
 * edge to contact, the beep <em>pitch rises</em> and the beep <em>period shrinks</em> (beeps land
 * closer together). Both are driven by a single {@code closeness} in {@code [0, 1]} — {@code 0} at the
 * edge, {@code 1} at contact — so pitch is monotonically non-decreasing and period monotonically
 * non-increasing in closeness, giving the player a smooth, legible "it's getting nearer" cue. The
 * radius guard ({@code proximityRadius <= 0}) both models a disabled scan and prevents a divide-by-zero
 * in the closeness ratio.
 *
 * <p>Headless and side-effect free: it computes the sound parameters; playing them is the caller's job.
 */
public final class ProximityWarning {

    private ProximityWarning() {}

    /** Beep pitch at the scan edge (farthest). */
    public static final float PITCH_FAR = 0.8f;

    /** Beep pitch at contact (nearest); Minecraft caps sound pitch at 2.0. */
    public static final float PITCH_NEAR = 2.0f;

    /** Ticks between beeps at the scan edge (slowest cadence). */
    public static final int PERIOD_FAR = 20;

    /** Ticks between beeps at contact (fastest cadence). */
    public static final int PERIOD_NEAR = 3;

    /**
     * The warning to emit for a given nearest distance.
     *
     * @param play whether to beep this window at all
     * @param pitch the beep pitch
     * @param periodTicks ticks to wait before the next beep
     */
    public record Warning(boolean play, float pitch, int periodTicks) {}

    /**
     * Maps the nearest-entity distance to a warning. Returns a silent {@link Warning} (no beep) when the
     * scan is disabled ({@code proximityRadius <= 0}) or the entity is beyond the radius; otherwise a
     * beep whose pitch and cadence intensify with closeness.
     *
     * @param nearestDistance distance to the nearest living entity, in blocks
     * @param proximityRadius the configured scan radius, in blocks
     * @return the warning parameters
     */
    public static Warning at(double nearestDistance, int proximityRadius) {
        if (proximityRadius <= 0 || nearestDistance > proximityRadius) {
            return new Warning(false, PITCH_FAR, PERIOD_FAR);
        }
        double closeness = 1.0 - clamp(nearestDistance / proximityRadius, 0.0, 1.0);
        float pitch = (float) (PITCH_FAR + closeness * (PITCH_NEAR - PITCH_FAR));
        int periodTicks = (int) Math.round(PERIOD_FAR - closeness * (PERIOD_FAR - PERIOD_NEAR));
        return new Warning(true, pitch, periodTicks);
    }

    private static double clamp(double value, double lo, double hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
