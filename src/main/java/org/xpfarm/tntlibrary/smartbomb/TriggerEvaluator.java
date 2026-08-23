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

/**
 * Pure per-tick trigger decision for an armed Smart Bomb.
 *
 * <p><strong>Rule (owner-confirmed).</strong> The three triggers combine with <em>OR-semantics</em>,
 * first-to-fire: the bomb detonates the moment <em>any</em> enabled trigger is satisfied. The delay is
 * the <em>guaranteed upper-bound cap</em> — proximity and time can pre-empt it, but once
 * {@code elapsedTicks} reaches {@code delayTicks} the bomb always goes off, so it can never hang armed
 * forever. When several triggers fire on the same tick the {@link Decision#firedBy()} label follows a
 * fixed precedence, {@code PROXIMITY > TIME > DELAY}, only to report <em>why</em> it detonated; the
 * {@code detonate} outcome does not depend on precedence. The whole rule lives in the single boolean
 * expression in {@link #evaluate} so it is trivial to adjust in one place if the contract changes.
 *
 * <p>Headless and side-effect free: no Bukkit, no clock, no randomness. The caller supplies the tick's
 * facts via {@link State} (with {@code worldTime} already normalized to {@code 0..23999}).
 */
public final class TriggerEvaluator {

    private TriggerEvaluator() {}

    /** Which trigger caused a detonation, or {@link #NONE} when the bomb stays armed. */
    public enum Trigger {
        /** A living entity was within the proximity radius. */
        PROXIMITY,
        /**
         * The time trigger fires when the world clock reaches or crosses the set time (robust to the
         * watcher's multi-tick sampling cadence and to {@code /time set} jumps).
         */
        TIME,
        /** The elapsed fuse reached the delay cap. */
        DELAY,
        /** No trigger fired this tick. */
        NONE
    }

    /** Outcome of one evaluation: whether to detonate, and the label of the winning trigger. */
    public record Decision(boolean detonate, Trigger firedBy) {}

    /**
     * The tick's observable facts.
     *
     * @param elapsedTicks ticks since the bomb was armed
     * @param worldTime world time-of-day, already normalized to {@code 0..23999} by the caller
     * @param previousWorldTime the world time sampled on the PRIOR watcher tick, already normalized to
     *     {@code 0..23999}; equal to {@code worldTime} on the first tick. Lets the evaluator detect the
     *     world clock reaching or crossing the time trigger between two multi-tick samples.
     * @param nearestDistance distance to the nearest living entity in scan range, or {@code null} when
     *     none is in range
     */
    public record State(long elapsedTicks, long worldTime, long previousWorldTime, Double nearestDistance) {}

    /**
     * Decides whether the bomb detonates this tick. See the class Javadoc for the rule.
     *
     * @param p the bomb's configuration
     * @param s the tick's facts
     * @return the detonation decision and the winning trigger label
     */
    public static Decision evaluate(SmartBombParams p, State s) {
        boolean proxFires =
                p.proximity() && s.nearestDistance() != null && s.nearestDistance() <= p.proximityRadius();
        boolean timeFires = false;
        if (p.timeTrigger() != null) {
            long target = p.timeTrigger();               // already normalized 0..23999 by the record
            long advanced = Math.floorMod(s.worldTime() - s.previousWorldTime(), 24000L);
            long toTarget = Math.floorMod(target - s.previousWorldTime(), 24000L);
            timeFires = s.worldTime() == target || (toTarget > 0 && toTarget <= advanced);
        }
        boolean delayFires = s.elapsedTicks() >= p.delayTicks();

        boolean detonate = proxFires || timeFires || delayFires;
        Trigger firedBy =
                proxFires ? Trigger.PROXIMITY : timeFires ? Trigger.TIME : delayFires ? Trigger.DELAY : Trigger.NONE;
        return new Decision(detonate, firedBy);
    }
}
