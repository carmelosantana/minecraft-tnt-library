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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pins the pure {@link TwinColor} enum — the single source of the Twin id strings and the inverse
 * White/Black pairing. These assertions run without a server (no {@link org.bukkit.Bukkit} calls):
 * {@code TwinColor} touches no Bukkit API and is fully unit-testable headless.
 */
final class TwinColorTest {

    @Test
    void oppositeIsTheInverseColour() {
        assertEquals(TwinColor.BLACK, TwinColor.WHITE.opposite());
        assertEquals(TwinColor.WHITE, TwinColor.BLACK.opposite());
    }

    @Test
    void variantIdsAreTheStableShippedStrings() {
        assertEquals("twins_white", TwinColor.WHITE.variantId());
        assertEquals("twins_black", TwinColor.BLACK.variantId());
    }

    @Test
    void fromVariantIdResolvesKnownVariants() {
        assertEquals(Optional.of(TwinColor.WHITE), TwinColor.fromVariantId("twins_white"));
        assertEquals(Optional.of(TwinColor.BLACK), TwinColor.fromVariantId("twins_black"));
    }

    @Test
    void fromVariantIdRejectsNonVariants() {
        assertEquals(Optional.empty(), TwinColor.fromVariantId("twins"));
        assertEquals(Optional.empty(), TwinColor.fromVariantId("waterbomb"));
        assertEquals(Optional.empty(), TwinColor.fromVariantId(null));
        assertEquals(Optional.empty(), TwinColor.fromVariantId(""));
    }

    @Test
    void isVariantMatchesOnlyTheTwoVariantIds() {
        assertTrue(TwinColor.isVariant("twins_white"));
        assertTrue(TwinColor.isVariant("twins_black"));
        assertFalse(TwinColor.isVariant("twins"));
        assertFalse(TwinColor.isVariant(null));
    }

    @Test
    void baseIdCollapsesVariantsAndPassesEverythingElseThrough() {
        assertEquals("twins", TwinColor.baseId("twins_white"));
        assertEquals("twins", TwinColor.baseId("twins_black"));
        assertEquals("waterbomb", TwinColor.baseId("waterbomb"));
        assertEquals("twins", TwinColor.baseId("twins"));
    }

    @Test
    void variantIdRoundTripsAndIsDistinctPerColour() {
        Set<String> seen = new HashSet<>();
        for (TwinColor c : TwinColor.values()) {
            assertEquals(Optional.of(c), TwinColor.fromVariantId(c.variantId()));
            assertTrue(seen.add(c.variantId()), "duplicate variantId " + c.variantId());
        }
    }
}
