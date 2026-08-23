/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.rig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Pins the on-the-wire strings {@link RigState} persists under {@code Keys.RIG_STATE} — the values
 * the detonation layer reads back — and the round-trip through {@link RigState#fromWire(String)}.
 */
final class RigStateTest {

    @Test
    void wireStringsAreExactlyPlacedAndPrimed() {
        assertEquals("placed", RigState.PLACED.wire());
        assertEquals("primed", RigState.PRIMED.wire());
    }

    @Test
    void fromWireRoundTripsEveryState() {
        for (RigState state : RigState.values()) {
            assertSame(state, RigState.fromWire(state.wire()).orElseThrow());
        }
    }

    @Test
    void fromWireIsEmptyForNullOrUnknown() {
        assertTrue(RigState.fromWire(null).isEmpty());
        assertEquals(Optional.empty(), RigState.fromWire("detonated"));
        assertEquals(Optional.empty(), RigState.fromWire("PLACED"));
    }
}
