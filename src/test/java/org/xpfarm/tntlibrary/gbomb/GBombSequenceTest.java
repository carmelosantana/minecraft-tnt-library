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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the pure launch/hang/slam state machine: {@link GBombSequence#phaseAt(long, long)} maps an
 * elapsed-tick counter onto exactly one {@link LaunchPhase} at every boundary, and the {@code
 * hangTicks < 1} programming-error guard throws.
 */
final class GBombSequenceTest {

    @Test
    void tickZeroIsLaunch() {
        assertEquals(LaunchPhase.LAUNCH, GBombSequence.phaseAt(0, 50));
    }

    @Test
    void nonPositiveElapsedIsLaunch() {
        assertEquals(LaunchPhase.LAUNCH, GBombSequence.phaseAt(-1, 50));
        assertEquals(LaunchPhase.LAUNCH, GBombSequence.phaseAt(-1000, 50));
    }

    @Test
    void firstTickAfterLaunchIsHang() {
        assertEquals(LaunchPhase.HANG, GBombSequence.phaseAt(1, 50));
    }

    @Test
    void tickJustBeforeHangEndIsHang() {
        assertEquals(LaunchPhase.HANG, GBombSequence.phaseAt(49, 50));
    }

    @Test
    void tickAtHangEndIsSlam() {
        assertEquals(LaunchPhase.SLAM, GBombSequence.phaseAt(50, 50));
    }

    @Test
    void tickAfterHangEndIsDone() {
        assertEquals(LaunchPhase.DONE, GBombSequence.phaseAt(51, 50));
        assertEquals(LaunchPhase.DONE, GBombSequence.phaseAt(9999, 50));
    }

    @Test
    void hangOfOneCollapsesLaunchSlamDone() {
        assertEquals(LaunchPhase.LAUNCH, GBombSequence.phaseAt(0, 1));
        assertEquals(LaunchPhase.SLAM, GBombSequence.phaseAt(1, 1));
        assertEquals(LaunchPhase.DONE, GBombSequence.phaseAt(2, 1));
    }

    @Test
    void isTerminalIsTrueOnlyForDone() {
        assertTrue(GBombSequence.isTerminal(LaunchPhase.DONE));
        assertFalse(GBombSequence.isTerminal(LaunchPhase.LAUNCH));
        assertFalse(GBombSequence.isTerminal(LaunchPhase.HANG));
        assertFalse(GBombSequence.isTerminal(LaunchPhase.SLAM));
    }

    @Test
    void hangBelowOneIsAProgrammingErrorGuard() {
        assertThrows(IllegalArgumentException.class, () -> GBombSequence.phaseAt(0, 0));
        assertThrows(IllegalArgumentException.class, () -> GBombSequence.phaseAt(5, -1));
    }
}
