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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Pins the permission-node strings the code checks. These exact strings must also be declared in
 * {@code plugin.yml} (asserted by {@code PluginDescriptorTest}); pinning them here catches a rename
 * on the code side.
 */
final class PermissionsTest {

    @Test
    void adminCommandNodesAreStable() {
        assertEquals("tntlibrary.admin", Permissions.ADMIN);
        assertEquals("tntlibrary.command.give", Permissions.GIVE);
        assertEquals("tntlibrary.command.reload", Permissions.RELOAD);
    }

    @Test
    void useNodeIsThePrefixPlusBombId() {
        assertEquals("tntlibrary.use.waterbomb", Permissions.use("waterbomb"));
        assertEquals("tntlibrary.use.", Permissions.USE_PREFIX);
    }

    @Test
    void useRejectsNullBombId() {
        assertThrows(NullPointerException.class, () -> Permissions.use(null));
    }
}
