/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.twins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pins the pure {@link PlacedTwinIndex} contract — per-world isolation, overwrite-on-re-add, O(1)
 * membership, and the unmodifiable-snapshot guarantee {@link PlacedTwinIndex#inWorld(UUID)} makes.
 * Runs headless (no {@link org.bukkit.Bukkit} calls).
 */
final class PlacedTwinIndexTest {

    private static final UUID WORLD_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID WORLD_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static PlacedTwin twin(UUID world, int x, int y, int z, TwinColor color) {
        return new PlacedTwin(new TwinLocation(world, x, y, z), color);
    }

    @Test
    void addThenContainsIsTrueAndSizeReflectsCount() {
        PlacedTwinIndex index = new PlacedTwinIndex();
        TwinLocation loc = new TwinLocation(WORLD_A, 1, 2, 3);
        index.add(new PlacedTwin(loc, TwinColor.WHITE));

        assertTrue(index.contains(loc));
        assertEquals(1, index.size());
    }

    @Test
    void twoTwinsAtDifferentLocationsBothAppearInWorld() {
        PlacedTwinIndex index = new PlacedTwinIndex();
        PlacedTwin a = twin(WORLD_A, 0, 0, 0, TwinColor.WHITE);
        PlacedTwin b = twin(WORLD_A, 10, 0, 0, TwinColor.BLACK);
        index.add(a);
        index.add(b);

        Collection<PlacedTwin> inA = index.inWorld(WORLD_A);
        assertEquals(2, inA.size());
        assertTrue(inA.contains(a));
        assertTrue(inA.contains(b));
        assertEquals(2, index.size());
    }

    @Test
    void reAddingAtSameLocationOverwritesWithoutGrowing() {
        PlacedTwinIndex index = new PlacedTwinIndex();
        TwinLocation loc = new TwinLocation(WORLD_A, 5, 5, 5);
        index.add(new PlacedTwin(loc, TwinColor.WHITE));
        index.add(new PlacedTwin(loc, TwinColor.BLACK));

        assertEquals(1, index.size());
        Collection<PlacedTwin> inA = index.inWorld(WORLD_A);
        assertEquals(1, inA.size());
        assertTrue(inA.contains(new PlacedTwin(loc, TwinColor.BLACK)));
        assertFalse(inA.contains(new PlacedTwin(loc, TwinColor.WHITE)));
    }

    @Test
    void removeDeletesTheTwinAndDecrementsSize() {
        PlacedTwinIndex index = new PlacedTwinIndex();
        TwinLocation loc = new TwinLocation(WORLD_A, 1, 1, 1);
        index.add(new PlacedTwin(loc, TwinColor.WHITE));

        index.remove(loc);

        assertFalse(index.contains(loc));
        assertEquals(0, index.size());
    }

    @Test
    void removeOfAbsentLocationIsANoOp() {
        PlacedTwinIndex index = new PlacedTwinIndex();
        index.add(twin(WORLD_A, 0, 0, 0, TwinColor.WHITE));

        index.remove(new TwinLocation(WORLD_A, 9, 9, 9));

        assertEquals(1, index.size());
    }

    @Test
    void worldsAreIsolated() {
        PlacedTwinIndex index = new PlacedTwinIndex();
        PlacedTwin inA = twin(WORLD_A, 0, 0, 0, TwinColor.WHITE);
        index.add(inA);

        assertTrue(index.inWorld(WORLD_B).isEmpty());
        assertFalse(index.inWorld(WORLD_B).contains(inA));
        assertEquals(1, index.inWorld(WORLD_A).size());
    }

    @Test
    void sameCoordsInDifferentWorldsAreDistinctEntries() {
        PlacedTwinIndex index = new PlacedTwinIndex();
        PlacedTwin a = twin(WORLD_A, 7, 7, 7, TwinColor.WHITE);
        PlacedTwin b = twin(WORLD_B, 7, 7, 7, TwinColor.BLACK);
        index.add(a);
        index.add(b);

        assertEquals(2, index.size());
        assertEquals(1, index.inWorld(WORLD_A).size());
        assertEquals(1, index.inWorld(WORLD_B).size());
        assertTrue(index.contains(a.location()));
        assertTrue(index.contains(b.location()));
    }

    @Test
    void inWorldOfUnknownWorldIsEmptyAndNonNull() {
        PlacedTwinIndex index = new PlacedTwinIndex();
        Collection<PlacedTwin> result = index.inWorld(WORLD_A);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void inWorldIsASnapshotUnaffectedByLaterMutation() {
        PlacedTwinIndex index = new PlacedTwinIndex();
        index.add(twin(WORLD_A, 0, 0, 0, TwinColor.WHITE));

        Collection<PlacedTwin> snapshot = index.inWorld(WORLD_A);
        assertEquals(1, snapshot.size());

        // Mutate the index after taking the snapshot; the snapshot must not change.
        index.add(twin(WORLD_A, 1, 0, 0, TwinColor.BLACK));
        index.remove(new TwinLocation(WORLD_A, 0, 0, 0));

        assertEquals(1, snapshot.size());
    }

    @Test
    void inWorldSnapshotIsUnmodifiable() {
        PlacedTwinIndex index = new PlacedTwinIndex();
        index.add(twin(WORLD_A, 0, 0, 0, TwinColor.WHITE));

        Collection<PlacedTwin> snapshot = index.inWorld(WORLD_A);
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.add(twin(WORLD_A, 2, 2, 2, TwinColor.BLACK)));
    }

    @Test
    void addRejectsNull() {
        PlacedTwinIndex index = new PlacedTwinIndex();
        assertThrows(NullPointerException.class, () -> index.add(null));
    }

    @Test
    void placedTwinRejectsNullComponents() {
        assertThrows(NullPointerException.class,
                () -> new PlacedTwin(null, TwinColor.WHITE));
        assertThrows(NullPointerException.class,
                () -> new PlacedTwin(new TwinLocation(WORLD_A, 0, 0, 0), null));
    }
}
