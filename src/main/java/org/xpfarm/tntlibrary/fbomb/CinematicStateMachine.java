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
 * The pure tick-to-phase mathematics of the F-Bomb cinematic, factored out of the director so it
 * can be unit tested without a live server. The single method is static and side-effect free: it
 * maps an elapsed cinematic {@code tick} (plus the bomb's total {@code menaceTicks} window) onto
 * the current {@link CinematicPhase}.
 *
 * <p>Mirrors the {@code GrowthModel.phaseFor} pure-function idiom from the reference plugin
 * (TuesdayTwister). Callers are expected to guarantee {@code menaceTicks > SUMMON_TICKS}, which
 * the F-Bomb's menace-ticks clamp floor of 20 already ensures.
 */
public final class CinematicStateMachine {

    /** Ticks the SUMMON fade-in covers, starting at (and including) tick 0. */
    public static final int SUMMON_TICKS = 10;

    private CinematicStateMachine() {
    }

    /**
     * The phase at a given elapsed cinematic {@code tick} within a {@code menaceTicks}-tick
     * window: {@link CinematicPhase#SUMMON} for {@code tick < SUMMON_TICKS} (including tick 0 and
     * negative ticks), {@link CinematicPhase#MENACE} for {@code SUMMON_TICKS <= tick <
     * menaceTicks}, {@link CinematicPhase#BLAST} at exactly {@code tick == menaceTicks}, and
     * {@link CinematicPhase#DONE} for {@code tick > menaceTicks}.
     */
    public static CinematicPhase phaseAt(long tick, int menaceTicks) {
        if (tick < SUMMON_TICKS) {
            return CinematicPhase.SUMMON;
        }
        if (tick < menaceTicks) {
            return CinematicPhase.MENACE;
        }
        if (tick == menaceTicks) {
            return CinematicPhase.BLAST;
        }
        return CinematicPhase.DONE;
    }
}
