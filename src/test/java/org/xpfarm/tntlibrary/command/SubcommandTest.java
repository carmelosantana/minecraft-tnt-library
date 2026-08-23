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

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Pins {@code /tntlibrary} subcommand routing — the one server-free decision the executor makes. */
final class SubcommandTest {

    @Test
    void resolvesKnownWordsCaseInsensitively() {
        assertEquals(Optional.of(Subcommand.GIVE), Subcommand.fromArg("give"));
        assertEquals(Optional.of(Subcommand.LIST), Subcommand.fromArg("LIST"));
        assertEquals(Optional.of(Subcommand.RELOAD), Subcommand.fromArg("Reload"));
    }

    @Test
    void rejectsUnknownOrNull() {
        assertTrue(Subcommand.fromArg("boom").isEmpty());
        assertTrue(Subcommand.fromArg("").isEmpty());
        assertTrue(Subcommand.fromArg(null).isEmpty());
    }

    @Test
    void labelIsTheLowercaseTypedWord() {
        assertEquals("give", Subcommand.GIVE.label());
        assertEquals("list", Subcommand.LIST.label());
        assertEquals("reload", Subcommand.RELOAD.label());
    }
}
