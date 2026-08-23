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

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * The pairing math for "The Twins" bomb: given an igniting Twin, pick the partner it detonates with.
 *
 * <h2>What "the nearest partner" means</h2>
 *
 * <p>When a Twin ignites, it seeks the single closest <em>opposite-colour</em> Twin to fire the beam
 * at. {@link #nearestOpposite} is that selection expressed as a pure function over a candidate
 * collection — the caller (the runtime adapter) supplies the world's placed Twins from
 * {@link PlacedTwinIndex}; this class decides which one wins. A candidate qualifies iff it is the
 * OPPOSITE colour, in the SAME world as the origin, at a DIFFERENT location than the origin, and
 * within {@code maxDistance} inclusive straight-line blocks. The origin's own colour is passed in
 * rather than read from a candidate because the igniting Twin is not necessarily in the candidate set.
 *
 * <h2>Why the tie-break is a total order</h2>
 *
 * <p>Two candidates can sit at exactly the same distance (e.g. {@code (0,3,4)} and {@code (3,4,0)}
 * are both 5 blocks from the origin). Detonation must be reproducible, so ties are broken by
 * {@code (x, then y, then z)} ascending — a total order over positions. Because that order does not
 * consult iteration order, the winner is identical no matter how the caller's collection is
 * enumerated, which the tests pin with two opposite input orderings.
 *
 * <h2>Pure</h2>
 *
 * <p>No {@link org.bukkit.Bukkit} API, no index internals, no mutation of the input collection —
 * just distance math over values, so it is fully unit-testable headless.
 */
public final class TwinsPairing {

    /**
     * Orders qualifying candidates: nearest first, ties broken by (x, then y, then z) ascending so
     * the minimum is a single, iteration-order-independent element.
     */
    private static final Comparator<PlacedTwin> BY_LOCATION_TUPLE =
            Comparator.<PlacedTwin>comparingInt(t -> t.location().x())
                    .thenComparingInt(t -> t.location().y())
                    .thenComparingInt(t -> t.location().z());

    private TwinsPairing() {}

    /**
     * The nearest valid partner for a Twin of {@code originColor} igniting at {@code origin}, or
     * empty if none qualifies. A candidate qualifies iff: it is the OPPOSITE colour, in the SAME
     * world as {@code origin}, at a DIFFERENT location than {@code origin}, and within
     * {@code maxDistance} (inclusive) straight-line blocks. The nearest qualifying candidate wins;
     * ties broken deterministically by (x, then y, then z) ascending so the result is stable
     * regardless of the iteration order of {@code candidates}. The input collection is not mutated.
     */
    public static Optional<PlacedTwin> nearestOpposite(
            TwinLocation origin, TwinColor originColor,
            Collection<PlacedTwin> candidates, double maxDistance) {
        TwinColor wanted = originColor.opposite();
        return candidates.stream()
                .filter(candidate -> candidate.color() == wanted)
                .filter(candidate -> origin.sameWorld(candidate.location()))
                .filter(candidate -> !origin.equals(candidate.location()))
                .filter(candidate -> origin.distanceTo(candidate.location()) <= maxDistance)
                .min(Comparator.comparingDouble(
                                (PlacedTwin candidate) -> origin.distanceTo(candidate.location()))
                        .thenComparing(BY_LOCATION_TUPLE));
    }
}
