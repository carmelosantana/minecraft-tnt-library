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
 * Pure, stateless mapper from an elapsed-tick counter onto a {@link LaunchPhase}.
 *
 * <p>The runtime {@code GBombSequenceTask} owns the mutable state (a tick counter that increments each
 * tick); this class holds none of it. Given how many ticks have elapsed since arming and the
 * configured apex hang length, {@link #phaseAt(long, long)} returns exactly one phase so the tick loop
 * can decide what to do without branching logic of its own. Keeping the decision here — free of any
 * Bukkit import — is what makes the launch/hang/slam machine headlessly unit-testable.
 *
 * <p>The timeline is: tick {@code 0} (and any non-positive counter) is {@link LaunchPhase#LAUNCH}; ticks
 * {@code 1..hangTicks-1} are {@link LaunchPhase#HANG}; the boundary tick {@code hangTicks} is the single
 * {@link LaunchPhase#SLAM} tick; every tick past it is the terminal {@link LaunchPhase#DONE}. When
 * {@code hangTicks == 1} the HANG window is empty and the timeline collapses to
 * {@code LAUNCH(0) → SLAM(1) → DONE(2)}.
 */
public final class GBombSequence {

    private GBombSequence() {}

    /**
     * Maps an elapsed-tick counter onto its {@link LaunchPhase}.
     *
     * @param elapsedTicks ticks elapsed since arming; {@code <= 0} is treated as the launch tick
     * @param hangTicks the configured apex hang length, in ticks; must be {@code >= 1}
     * @return the phase the detonation is in at {@code elapsedTicks}
     * @throws IllegalArgumentException if {@code hangTicks < 1}. This is a programming-error guard, not
     *     input validation: {@link GBombParams} already clamps {@code hangTicks} to {@code >= 1}, so a
     *     value below 1 here means a caller bypassed the params contract.
     */
    public static LaunchPhase phaseAt(long elapsedTicks, long hangTicks) {
        if (hangTicks < 1) {
            throw new IllegalArgumentException("hangTicks must be >= 1, was " + hangTicks);
        }
        if (elapsedTicks <= 0) {
            return LaunchPhase.LAUNCH;
        }
        if (elapsedTicks < hangTicks) {
            return LaunchPhase.HANG;
        }
        if (elapsedTicks == hangTicks) {
            return LaunchPhase.SLAM;
        }
        return LaunchPhase.DONE;
    }

    /**
     * Whether the phase is terminal — i.e. the detonation has finished and the task may cancel.
     *
     * @param p the phase to test
     * @return {@code true} only for {@link LaunchPhase#DONE}
     */
    public static boolean isTerminal(LaunchPhase p) {
        return p == LaunchPhase.DONE;
    }
}
