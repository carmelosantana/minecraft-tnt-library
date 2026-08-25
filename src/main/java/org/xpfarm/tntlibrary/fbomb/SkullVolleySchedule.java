/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.fbomb;

import java.util.ArrayList;
import java.util.List;

/**
 * The pure, bounded fire-tick schedule for the F-Bomb's WitherSkull volley: the ticks during the
 * MENACE window at which the apparition fires a skull, bounded by count, spaced by cadence,
 * starting after the SUMMON fade, and never at or after the BLAST tick.
 *
 * <p>Pure, world-independent math; no Bukkit types involved, so this class is unit-tested
 * directly with no server.
 */
public final class SkullVolleySchedule {

    private SkullVolleySchedule() {
    }

    /**
     * The sorted ascending ticks at which a skull fires: {@code startTick + i*cadenceTicks} for
     * {@code i = 1..skullCount}, dropping any tick {@code >= menaceTicks} so the volley never
     * lands at or after the BLAST tick and never runs away. {@code skullCount <= 0} or
     * {@code cadenceTicks <= 0} yields an empty list.
     */
    public static List<Long> fireTicks(int skullCount, int cadenceTicks, int startTick, int menaceTicks) {
        if (skullCount <= 0 || cadenceTicks <= 0) {
            return List.of();
        }
        List<Long> ticks = new ArrayList<>();
        for (int i = 1; i <= skullCount; i++) {
            long t = (long) startTick + (long) i * cadenceTicks;
            if (t >= menaceTicks) {
                break;
            }
            ticks.add(t);
        }
        return List.copyOf(ticks);
    }

    /** Membership convenience: whether {@code tick} is one of {@link #fireTicks}'s ticks. */
    public static boolean firesAt(long tick, int skullCount, int cadenceTicks, int startTick, int menaceTicks) {
        return fireTicks(skullCount, cadenceTicks, startTick, menaceTicks).contains(tick);
    }
}
