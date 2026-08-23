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

import java.util.List;

/**
 * The pure decision an igniting Twin reaches — the result of {@link TwinsPlan#resolve}. A Twin's
 * ignition either finds a valid opposite partner in range and fires the beam ({@link Paired}) or
 * finds none and does nothing ({@link Fizzle}). Modelled as a sealed interface so callers switch
 * over exactly these two cases with compiler-enforced exhaustiveness (no {@code default} needed),
 * and so the two shapes never leak beyond this pair.
 *
 * <p>Carries no {@link org.bukkit.Bukkit} state: it is a plain value the runtime adapter
 * ({@code TheTwins.detonate}) reads to spawn particles and spend the Twins.
 */
public sealed interface TwinsOutcome permits TwinsOutcome.Paired, TwinsOutcome.Fizzle {

    /**
     * A valid opposite partner was found: carve the beam and spend both Twins.
     *
     * @param partner the nearest opposite Twin the ignition paired with
     * @param beam the {@link TwinsBeam} sample points from the origin's centre to the partner's
     *     centre, in origin&rarr;partner order; never empty
     */
    record Paired(PlacedTwin partner, List<Vec3> beam) implements TwinsOutcome {}

    /** No valid partner in range: the ignition fizzles and nothing is carved or spent. */
    record Fizzle() implements TwinsOutcome {}
}
