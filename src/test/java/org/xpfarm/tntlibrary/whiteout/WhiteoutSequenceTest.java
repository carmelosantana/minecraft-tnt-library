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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the pure pull -> collapse -> sweep -> done state machine: {@link WhiteoutSequence#phaseAt} maps
 * an elapsed-tick counter onto exactly one {@link WhiteoutPhase} at every boundary, and the
 * out-of-range guards throw.
 */
final class WhiteoutSequenceTest {

    @Test
    void tickZeroAndNegativesArePull() {
        assertEquals(WhiteoutPhase.PULL, WhiteoutSequence.phaseAt(0, 60, 5));
        assertEquals(WhiteoutPhase.PULL, WhiteoutSequence.phaseAt(-3, 60, 5));
    }

    @Test
    void ticksBeforePullEndArePull() {
        assertEquals(WhiteoutPhase.PULL, WhiteoutSequence.phaseAt(1, 60, 5));
        assertEquals(WhiteoutPhase.PULL, WhiteoutSequence.phaseAt(59, 60, 5));
    }

    @Test
    void exactlyAtPullEndIsCollapse() {
        assertEquals(WhiteoutPhase.COLLAPSE, WhiteoutSequence.phaseAt(60, 60, 5));
    }

    @Test
    void ticksInsideTheSweepWindowAreSweep() {
        assertEquals(WhiteoutPhase.SWEEP, WhiteoutSequence.phaseAt(61, 60, 5));
        assertEquals(WhiteoutPhase.SWEEP, WhiteoutSequence.phaseAt(65, 60, 5)); // pullTicks+sweepTicks
    }

    @Test
    void ticksPastTheSweepWindowAreDone() {
        assertEquals(WhiteoutPhase.DONE, WhiteoutSequence.phaseAt(66, 60, 5));
        assertEquals(WhiteoutPhase.DONE, WhiteoutSequence.phaseAt(9999, 60, 5));
    }

    @Test
    void zeroSweepCollapsesToPullCollapseDone() {
        assertEquals(WhiteoutPhase.PULL, WhiteoutSequence.phaseAt(0, 1, 0));
        assertEquals(WhiteoutPhase.COLLAPSE, WhiteoutSequence.phaseAt(1, 1, 0));
        assertEquals(WhiteoutPhase.DONE, WhiteoutSequence.phaseAt(2, 1, 0));
    }

    @Test
    void isTerminalIsTrueOnlyForDone() {
        assertTrue(WhiteoutSequence.isTerminal(WhiteoutPhase.DONE));
        assertFalse(WhiteoutSequence.isTerminal(WhiteoutPhase.PULL));
        assertFalse(WhiteoutSequence.isTerminal(WhiteoutPhase.COLLAPSE));
        assertFalse(WhiteoutSequence.isTerminal(WhiteoutPhase.SWEEP));
    }

    @Test
    void outOfRangeArgumentsThrow() {
        assertThrows(IllegalArgumentException.class, () -> WhiteoutSequence.phaseAt(0, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> WhiteoutSequence.phaseAt(0, 60, -1));
    }
}
