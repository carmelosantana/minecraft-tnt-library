/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.smartbomb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link BlockKey} contract: {@code format} round-trips through {@code parse} for every sign
 * of coordinate, a world name containing a comma survives the right-split, and malformed input parses
 * to {@code Optional.empty()} rather than throwing.
 */
final class BlockKeyTest {

    @Test
    void formatRoundTripsThroughParseForEverySign() {
        for (BlockKey key :
                new BlockKey[] {
                    new BlockKey("world", 1, 2, 3),
                    new BlockKey("world", -1, -2, -3),
                    new BlockKey("world", 0, 0, 0),
                    new BlockKey("world_nether", -100, 64, 12345)
                }) {
            assertEquals(Optional.of(key), BlockKey.parse(key.format()), "round-trip for " + key);
        }
    }

    @Test
    void formatIsCommaSeparatedWorldXyz() {
        assertEquals("world,-1,64,2", new BlockKey("world", -1, 64, 2).format());
    }

    @Test
    void worldNameContainingCommaStillRoundTrips() {
        BlockKey key = new BlockKey("weird,world", 1, 2, 3);
        assertEquals("weird,world,1,2,3", key.format());
        assertEquals(Optional.of(key), BlockKey.parse(key.format()));
    }

    @Test
    void parseRejectsTooFewFields() {
        assertTrue(BlockKey.parse("a,b,c").isEmpty());
    }

    @Test
    void parseRejectsNonIntCoordinate() {
        assertTrue(BlockKey.parse("w,1,2,x").isEmpty());
    }

    @Test
    void parseRejectsEmptyAndNull() {
        assertTrue(BlockKey.parse("").isEmpty());
        assertTrue(BlockKey.parse(null).isEmpty());
    }

    @Test
    void constructorRejectsNullWorld() {
        assertThrows(NullPointerException.class, () -> new BlockKey(null, 1, 2, 3));
    }

    @Test
    void constructorRejectsEmptyWorld() {
        assertThrows(IllegalArgumentException.class, () -> new BlockKey("", 1, 2, 3));
    }
}
