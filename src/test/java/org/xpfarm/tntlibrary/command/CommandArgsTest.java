/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Pins the give-amount rule: a whole number in {@code [1, 64]}, everything else rejected. */
final class CommandArgsTest {

    @Test
    void acceptsInRangeWholeNumbers() {
        assertEquals(OptionalInt.of(1), CommandArgs.parseAmount("1"));
        assertEquals(OptionalInt.of(64), CommandArgs.parseAmount("64"));
        assertEquals(OptionalInt.of(16), CommandArgs.parseAmount(" 16 "));
    }

    @Test
    void rejectsOutOfRange() {
        assertTrue(CommandArgs.parseAmount("0").isEmpty());
        assertTrue(CommandArgs.parseAmount("65").isEmpty());
        assertTrue(CommandArgs.parseAmount("-3").isEmpty());
    }

    @Test
    void rejectsNonNumbersAndNull() {
        assertTrue(CommandArgs.parseAmount("five").isEmpty());
        assertTrue(CommandArgs.parseAmount("1.5").isEmpty());
        assertTrue(CommandArgs.parseAmount("").isEmpty());
        assertTrue(CommandArgs.parseAmount(null).isEmpty());
    }

    @Test
    void boundsConstantsAreOneAndSixtyFour() {
        assertEquals(1, CommandArgs.MIN_AMOUNT);
        assertEquals(64, CommandArgs.MAX_AMOUNT);
    }
}
