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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CinematicStateMachine}. Headless: pure tick-to-phase math, no server.
 */
final class CinematicStateMachineTest {

    @Test
    void summonFadeCoversTheFirstTenTicks() {
        assertEquals(CinematicPhase.SUMMON, CinematicStateMachine.phaseAt(0, 60));
        assertEquals(CinematicPhase.SUMMON, CinematicStateMachine.phaseAt(-5, 60));
        assertEquals(CinematicPhase.SUMMON, CinematicStateMachine.phaseAt(9, 60));
    }

    @Test
    void menaceRunsFromTheFadeEndToTheBlast() {
        assertEquals(CinematicPhase.MENACE, CinematicStateMachine.phaseAt(10, 60));
        assertEquals(CinematicPhase.MENACE, CinematicStateMachine.phaseAt(59, 60));
    }

    @Test
    void exactlyAtWindowIsBlast() {
        assertEquals(CinematicPhase.BLAST, CinematicStateMachine.phaseAt(60, 60));
    }

    @Test
    void afterWindowIsDone() {
        assertEquals(CinematicPhase.DONE, CinematicStateMachine.phaseAt(61, 60));
        assertEquals(CinematicPhase.DONE, CinematicStateMachine.phaseAt(9999, 60));
    }

    @Test
    void summonTicksConstantIsTen() {
        assertEquals(10, CinematicStateMachine.SUMMON_TICKS);
    }
}
