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

/**
 * The pure heart of "The Twins" detonation: composes {@link TwinsPairing} and {@link TwinsBeam} into
 * a single {@link TwinsOutcome} decision. {@code TheTwins.detonate} is a thin runtime adapter that
 * calls {@link #resolve} and then applies the effects the outcome describes.
 *
 * <h2>What it composes</h2>
 *
 * <p>{@link #resolve} asks {@link TwinsPairing#nearestOpposite} for the nearest valid opposite-colour
 * partner in range; on a hit it asks {@link TwinsBeam#samplePoints} for the beam between the two block
 * centres and returns {@link TwinsOutcome.Paired}; on a miss it returns {@link TwinsOutcome.Fizzle}.
 * All pairing and beam math lives in those classes — this one only glues them together, so it stays
 * free of duplicated geometry.
 *
 * <h2>Pure</h2>
 *
 * <p>No {@link org.bukkit.Bukkit} API, no mutation of the input collection — just delegation over
 * values, so it is fully unit-testable headless.
 */
public final class TwinsPlan {

    private TwinsPlan() {}

    /**
     * Resolves what happens when a Twin of {@code color} ignites at {@code origin}: finds the nearest
     * opposite partner among {@code candidates} within {@code maxDistance}
     * ({@link TwinsPairing#nearestOpposite}); on a hit returns {@link TwinsOutcome.Paired} with the
     * partner and the {@link TwinsBeam#samplePoints} beam from origin to partner; on a miss returns
     * {@link TwinsOutcome.Fizzle}. The input collection is not mutated.
     *
     * @param origin the igniting Twin's block position
     * @param color the igniting Twin's colour (its partner must be the opposite colour)
     * @param candidates the world's other placed Twins to pair against
     * @param maxDistance the inclusive straight-line pairing range in blocks
     * @return {@link TwinsOutcome.Paired} when a valid partner is in range, else
     *     {@link TwinsOutcome.Fizzle}
     */
    public static TwinsOutcome resolve(TwinLocation origin, TwinColor color,
            Collection<PlacedTwin> candidates, double maxDistance) {
        return TwinsPairing.nearestOpposite(origin, color, candidates, maxDistance)
                .<TwinsOutcome>map(partner ->
                        new TwinsOutcome.Paired(
                                partner, TwinsBeam.samplePoints(origin, partner.location())))
                .orElseGet(TwinsOutcome.Fizzle::new);
    }
}
