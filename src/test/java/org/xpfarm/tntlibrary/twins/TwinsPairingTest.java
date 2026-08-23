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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pins the pure {@link TwinsPairing#nearestOpposite} contract — opposite-colour, same-world,
 * not-self, within-inclusive-range selection with a deterministic (x, y, z) tie-break. Runs headless
 * (no {@link org.bukkit.Bukkit} calls).
 */
final class TwinsPairingTest {

    private static final UUID WORLD_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID WORLD_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static final TwinLocation ORIGIN = new TwinLocation(WORLD_A, 0, 0, 0);

    private static PlacedTwin twin(UUID world, int x, int y, int z, TwinColor color) {
        return new PlacedTwin(new TwinLocation(world, x, y, z), color);
    }

    @Test
    void emptyCandidatesYieldEmpty() {
        Optional<PlacedTwin> result =
                TwinsPairing.nearestOpposite(ORIGIN, TwinColor.WHITE, List.of(), 100.0);

        assertTrue(result.isEmpty());
    }

    @Test
    void singleOppositeCandidateWithinRangeIsReturned() {
        PlacedTwin partner = twin(WORLD_A, 3, 0, 0, TwinColor.BLACK);

        Optional<PlacedTwin> result =
                TwinsPairing.nearestOpposite(ORIGIN, TwinColor.WHITE, List.of(partner), 100.0);

        assertEquals(Optional.of(partner), result);
    }

    @Test
    void singleSameColourCandidateWithinRangeYieldsEmpty() {
        PlacedTwin sameColour = twin(WORLD_A, 3, 0, 0, TwinColor.WHITE);

        Optional<PlacedTwin> result =
                TwinsPairing.nearestOpposite(ORIGIN, TwinColor.WHITE, List.of(sameColour), 100.0);

        assertTrue(result.isEmpty());
    }

    @Test
    void nearerOfTwoOppositeCandidatesIsReturned() {
        PlacedTwin near = twin(WORLD_A, 2, 0, 0, TwinColor.BLACK);
        PlacedTwin far = twin(WORLD_A, 10, 0, 0, TwinColor.BLACK);

        Optional<PlacedTwin> result =
                TwinsPairing.nearestOpposite(ORIGIN, TwinColor.WHITE, List.of(far, near), 100.0);

        assertEquals(Optional.of(near), result);
    }

    @Test
    void candidateExactlyAtMaxDistanceIsIncluded() {
        PlacedTwin atBound = twin(WORLD_A, 5, 0, 0, TwinColor.BLACK);

        Optional<PlacedTwin> result =
                TwinsPairing.nearestOpposite(ORIGIN, TwinColor.WHITE, List.of(atBound), 5.0);

        assertEquals(Optional.of(atBound), result);
    }

    @Test
    void candidateJustBeyondMaxDistanceIsExcluded() {
        PlacedTwin beyond = twin(WORLD_A, 6, 0, 0, TwinColor.BLACK);

        Optional<PlacedTwin> result =
                TwinsPairing.nearestOpposite(ORIGIN, TwinColor.WHITE, List.of(beyond), 5.0);

        assertTrue(result.isEmpty());
    }

    @Test
    void candidateInDifferentWorldIsExcludedEvenIfNearer() {
        // Numerically 1 block away, but in another world -> never qualifies.
        PlacedTwin otherWorld = twin(WORLD_B, 1, 0, 0, TwinColor.BLACK);

        Optional<PlacedTwin> result =
                TwinsPairing.nearestOpposite(ORIGIN, TwinColor.WHITE, List.of(otherWorld), 100.0);

        assertTrue(result.isEmpty());
    }

    @Test
    void candidateAtSameLocationAsOriginIsExcluded() {
        // Same coordinates as the origin, opposite colour -> a Twin never pairs with its own spot.
        PlacedTwin atOrigin = new PlacedTwin(ORIGIN, TwinColor.BLACK);

        Optional<PlacedTwin> result =
                TwinsPairing.nearestOpposite(ORIGIN, TwinColor.WHITE, List.of(atOrigin), 100.0);

        assertTrue(result.isEmpty());
    }

    @Test
    void mixedSetReturnsTheOnlyValidCandidate() {
        PlacedTwin sameColour = twin(WORLD_A, 2, 0, 0, TwinColor.WHITE);      // wrong colour
        PlacedTwin outOfRange = twin(WORLD_A, 50, 0, 0, TwinColor.BLACK);     // too far
        PlacedTwin otherWorld = twin(WORLD_B, 1, 0, 0, TwinColor.BLACK);      // other world
        PlacedTwin self = new PlacedTwin(ORIGIN, TwinColor.BLACK);            // same spot
        PlacedTwin valid = twin(WORLD_A, 4, 0, 0, TwinColor.BLACK);          // the one

        List<PlacedTwin> candidates =
                List.of(sameColour, outOfRange, otherWorld, self, valid);

        Optional<PlacedTwin> result =
                TwinsPairing.nearestOpposite(ORIGIN, TwinColor.WHITE, candidates, 10.0);

        assertEquals(Optional.of(valid), result);
    }

    @Test
    void tieBreakIsDeterministicRegardlessOfInputOrder() {
        // Two opposite candidates at equal distance (5) from the origin. The smaller (x, y, z)
        // tuple must win no matter what order the candidates arrive in.
        PlacedTwin lowTuple = twin(WORLD_A, 0, 3, 4, TwinColor.BLACK);   // (0,3,4)
        PlacedTwin highTuple = twin(WORLD_A, 3, 4, 0, TwinColor.BLACK);  // (3,4,0)
        assertEquals(ORIGIN.distanceTo(lowTuple.location()),
                ORIGIN.distanceTo(highTuple.location()),
                "test setup: the two candidates must be equidistant");

        Optional<PlacedTwin> forward =
                TwinsPairing.nearestOpposite(
                        ORIGIN, TwinColor.WHITE, List.of(lowTuple, highTuple), 100.0);
        Optional<PlacedTwin> reversed =
                TwinsPairing.nearestOpposite(
                        ORIGIN, TwinColor.WHITE, List.of(highTuple, lowTuple), 100.0);

        assertEquals(Optional.of(lowTuple), forward);
        assertEquals(Optional.of(lowTuple), reversed, "tie-break must not depend on input order");
    }

    @Test
    void inputCollectionIsNotMutated() {
        List<PlacedTwin> candidates = new ArrayList<>(List.of(
                twin(WORLD_A, 10, 0, 0, TwinColor.BLACK),
                twin(WORLD_A, 2, 0, 0, TwinColor.BLACK)));
        List<PlacedTwin> snapshot = new ArrayList<>(candidates);

        TwinsPairing.nearestOpposite(ORIGIN, TwinColor.WHITE, candidates, 100.0);

        assertEquals(snapshot, candidates, "nearestOpposite must not reorder or mutate its input");
    }
}
