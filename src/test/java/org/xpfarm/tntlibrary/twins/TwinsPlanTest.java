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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pins the pure {@link TwinsPlan#resolve} contract — it composes {@link TwinsPairing} and
 * {@link TwinsBeam} into a {@link TwinsOutcome}: a {@link TwinsOutcome.Paired} plan when a nearest
 * opposite partner is in range, otherwise a {@link TwinsOutcome.Fizzle}. Runs headless (no
 * {@link org.bukkit.Bukkit} calls). The beam assertions use list equality against
 * {@link TwinsBeam#samplePoints} to prove delegation rather than re-deriving the geometry.
 */
final class TwinsPlanTest {

    private static final UUID WORLD_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID WORLD_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static final TwinLocation ORIGIN = new TwinLocation(WORLD_A, 0, 0, 0);

    private static PlacedTwin twin(UUID world, int x, int y, int z, TwinColor color) {
        return new PlacedTwin(new TwinLocation(world, x, y, z), color);
    }

    @Test
    void emptyCandidatesFizzle() {
        TwinsOutcome outcome = TwinsPlan.resolve(ORIGIN, TwinColor.WHITE, List.of(), 100.0);

        assertInstanceOf(TwinsOutcome.Fizzle.class, outcome);
    }

    @Test
    void allSameColourFizzle() {
        List<PlacedTwin> candidates = List.of(
                twin(WORLD_A, 1, 0, 0, TwinColor.WHITE),
                twin(WORLD_A, 2, 0, 0, TwinColor.WHITE));

        TwinsOutcome outcome = TwinsPlan.resolve(ORIGIN, TwinColor.WHITE, candidates, 100.0);

        assertInstanceOf(TwinsOutcome.Fizzle.class, outcome);
    }

    @Test
    void allOutOfRangeFizzle() {
        List<PlacedTwin> candidates = List.of(twin(WORLD_A, 50, 0, 0, TwinColor.BLACK));

        TwinsOutcome outcome = TwinsPlan.resolve(ORIGIN, TwinColor.WHITE, candidates, 10.0);

        assertInstanceOf(TwinsOutcome.Fizzle.class, outcome);
    }

    @Test
    void differentWorldFizzle() {
        List<PlacedTwin> candidates = List.of(twin(WORLD_B, 1, 0, 0, TwinColor.BLACK));

        TwinsOutcome outcome = TwinsPlan.resolve(ORIGIN, TwinColor.WHITE, candidates, 100.0);

        assertInstanceOf(TwinsOutcome.Fizzle.class, outcome);
    }

    @Test
    void validOppositeInRangePairsWithThatPartner() {
        PlacedTwin partner = twin(WORLD_A, 4, 0, 0, TwinColor.BLACK);
        List<PlacedTwin> candidates = List.of(partner);

        TwinsOutcome outcome = TwinsPlan.resolve(ORIGIN, TwinColor.WHITE, candidates, 100.0);

        TwinsOutcome.Paired paired = assertInstanceOf(TwinsOutcome.Paired.class, outcome);
        assertEquals(partner, paired.partner());
    }

    @Test
    void pairedBeamDelegatesToTwinsBeam() {
        PlacedTwin partner = twin(WORLD_A, 4, 0, 0, TwinColor.BLACK);
        List<PlacedTwin> candidates = List.of(partner);

        TwinsOutcome outcome = TwinsPlan.resolve(ORIGIN, TwinColor.WHITE, candidates, 100.0);

        TwinsOutcome.Paired paired = assertInstanceOf(TwinsOutcome.Paired.class, outcome);
        List<Vec3> beam = paired.beam();

        assertFalse(beam.isEmpty(), "beam is non-empty");
        // Starts at origin's centre, ends at partner's centre.
        assertEquals(new Vec3(0.5, 0.5, 0.5), beam.get(0));
        assertEquals(new Vec3(4.5, 0.5, 0.5), beam.get(beam.size() - 1));
        // Whole beam equals TwinsBeam's own output — proves delegation, no duplicated math.
        assertEquals(TwinsBeam.samplePoints(ORIGIN, partner.location()), beam);
    }

    @Test
    void twoCandidatesPairWithTheNearer() {
        PlacedTwin near = twin(WORLD_A, 3, 0, 0, TwinColor.BLACK);
        PlacedTwin far = twin(WORLD_A, 9, 0, 0, TwinColor.BLACK);
        List<PlacedTwin> candidates = List.of(far, near);

        TwinsOutcome outcome = TwinsPlan.resolve(ORIGIN, TwinColor.WHITE, candidates, 100.0);

        TwinsOutcome.Paired paired = assertInstanceOf(TwinsOutcome.Paired.class, outcome);
        assertEquals(near, paired.partner());
        assertEquals(TwinsBeam.samplePoints(ORIGIN, near.location()), paired.beam());
    }

    @Test
    void outcomeIsDistinguishableViaSealedSwitch() {
        TwinsOutcome paired = TwinsPlan.resolve(
                ORIGIN, TwinColor.WHITE,
                List.of(twin(WORLD_A, 4, 0, 0, TwinColor.BLACK)), 100.0);
        TwinsOutcome fizzle = TwinsPlan.resolve(ORIGIN, TwinColor.WHITE, List.of(), 100.0);

        assertEquals("paired", describe(paired));
        assertEquals("fizzle", describe(fizzle));
    }

    /** Exhaustive sealed switch — no default branch, so the compiler enforces total coverage. */
    private static String describe(TwinsOutcome outcome) {
        return switch (outcome) {
            case TwinsOutcome.Paired p -> "paired";
            case TwinsOutcome.Fizzle f -> "fizzle";
        };
    }
}
