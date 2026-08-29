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
 * Pure, stateless mapper from an elapsed-tick counter onto a {@link WhiteoutPhase}. The runtime
 * {@code WhiteoutSequenceTask} owns the mutable tick counter; this class holds none of it. Keeping the
 * decision here — free of any Bukkit import — is what makes the pull/collapse/sweep machine headlessly
 * unit-testable.
 *
 * <p>Timeline: {@code elapsed < pullTicks} (and any non-positive counter) is {@link WhiteoutPhase#PULL};
 * the boundary tick {@code pullTicks} is the single {@link WhiteoutPhase#COLLAPSE} tick; ticks
 * {@code pullTicks+1 .. pullTicks+sweepTicks} are {@link WhiteoutPhase#SWEEP}; every tick past that is
 * {@link WhiteoutPhase#DONE}. When {@code sweepTicks == 0} the sweep window is empty and the timeline
 * collapses to {@code PULL -> COLLAPSE(pullTicks) -> DONE(pullTicks+1)}.
 */
public final class WhiteoutSequence {

    private WhiteoutSequence() {}

    /**
     * Maps an elapsed-tick counter onto its {@link WhiteoutPhase}.
     *
     * @param elapsed ticks elapsed since detonation; {@code <= 0} is treated as a pull tick
     * @param pullTicks the active pull window length, in ticks; must be {@code >= 1}
     * @param sweepTicks the scar-sweep window length, in ticks; must be {@code >= 0}
     * @throws IllegalArgumentException if {@code pullTicks < 1} or {@code sweepTicks < 0}. A
     *     programming-error guard: {@link WhiteoutParams} clamps {@code pullTicks >= 1}, and the runtime
     *     computes {@code sweepTicks >= 0} from {@link ScarGeometry}
     */
    public static WhiteoutPhase phaseAt(long elapsed, int pullTicks, int sweepTicks) {
        if (pullTicks < 1) {
            throw new IllegalArgumentException("pullTicks must be >= 1, was " + pullTicks);
        }
        if (sweepTicks < 0) {
            throw new IllegalArgumentException("sweepTicks must be >= 0, was " + sweepTicks);
        }
        if (elapsed < pullTicks) {
            return WhiteoutPhase.PULL;
        }
        if (elapsed == pullTicks) {
            return WhiteoutPhase.COLLAPSE;
        }
        if (elapsed <= (long) pullTicks + sweepTicks) {
            return WhiteoutPhase.SWEEP;
        }
        return WhiteoutPhase.DONE;
    }

    /**
     * Whether the phase is terminal — the detonation has finished and the task may cancel.
     *
     * @return {@code true} only for {@link WhiteoutPhase#DONE}
     */
    public static boolean isTerminal(WhiteoutPhase p) {
        return p == WhiteoutPhase.DONE;
    }
}
