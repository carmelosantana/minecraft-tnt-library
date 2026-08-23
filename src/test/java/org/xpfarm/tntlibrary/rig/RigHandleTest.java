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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

/**
 * Exercises the pure {@link RigHandle} value type: it carries the bomb id and both entity UUIDs,
 * rejects nulls, and defensively clones its {@link Location} so a rig's recorded cell cannot be
 * mutated by a caller. A world-less {@code Location} is a plain data holder, so no server is needed.
 */
final class RigHandleTest {

    private static Location loc() {
        return new Location(null, 10.0, 64.0, -7.0);
    }

    @Test
    void carriesItsIdentity() {
        UUID display = UUID.randomUUID();
        UUID interaction = UUID.randomUUID();
        RigHandle handle = new RigHandle("waterbomb", display, interaction, loc());

        assertEquals("waterbomb", handle.bombId());
        assertEquals(display, handle.blockDisplayId());
        assertEquals(interaction, handle.interactionId());
        assertEquals(loc(), handle.blockLocation());
    }

    @Test
    void rejectsNullComponents() {
        UUID id = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> new RigHandle(null, id, id, loc()));
        assertThrows(NullPointerException.class, () -> new RigHandle("b", null, id, loc()));
        assertThrows(NullPointerException.class, () -> new RigHandle("b", id, null, loc()));
        assertThrows(NullPointerException.class, () -> new RigHandle("b", id, id, null));
    }

    @Test
    void clonesLocationOnTheWayInSoLaterMutationDoesNotLeak() {
        UUID id = UUID.randomUUID();
        Location original = loc();
        RigHandle handle = new RigHandle("b", id, id, original);

        original.add(100.0, 0.0, 0.0);

        assertEquals(loc(), handle.blockLocation());
    }

    @Test
    void clonesLocationOnTheWayOutSoCallersCannotMutateTheStoredCell() {
        UUID id = UUID.randomUUID();
        RigHandle handle = new RigHandle("b", id, id, loc());

        Location returned = handle.blockLocation();
        returned.add(100.0, 0.0, 0.0);

        assertNotSame(returned, handle.blockLocation());
        assertEquals(loc(), handle.blockLocation());
    }
}
