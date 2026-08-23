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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pins the armed-bomb trigger rule: OR-semantics, delay as upper-bound cap, label proximity>time>delay. */
final class TriggerEvaluatorTest {

    /** delay=50, no time, no proximity. */
    private static SmartBombParams delayOnly() {
        return new SmartBombParams(4, 50, null, false, 6);
    }

    @Test
    void delayFiresAtCapNotBefore() {
        SmartBombParams p = delayOnly();
        assertFalse(TriggerEvaluator.evaluate(p, new TriggerEvaluator.State(49, 0, null)).detonate());
        TriggerEvaluator.Decision d = TriggerEvaluator.evaluate(p, new TriggerEvaluator.State(50, 0, null));
        assertTrue(d.detonate());
        assertEquals(TriggerEvaluator.Trigger.DELAY, d.firedBy());
    }

    @Test
    void timeFiresExactlyAtMatchNotOffByOne() {
        SmartBombParams p = new SmartBombParams(4, 72000, 6000L, false, 6);
        assertFalse(TriggerEvaluator.evaluate(p, new TriggerEvaluator.State(0, 5999, null)).detonate());
        assertFalse(TriggerEvaluator.evaluate(p, new TriggerEvaluator.State(0, 6001, null)).detonate());
        TriggerEvaluator.Decision d = TriggerEvaluator.evaluate(p, new TriggerEvaluator.State(0, 6000, null));
        assertTrue(d.detonate());
        assertEquals(TriggerEvaluator.Trigger.TIME, d.firedBy());
    }

    @Test
    void proximityFiresWithinRadiusOnly() {
        SmartBombParams p = new SmartBombParams(4, 72000, null, true, 6);
        // farther than radius: no fire
        assertFalse(TriggerEvaluator.evaluate(p, new TriggerEvaluator.State(0, 0, 6.5)).detonate());
        // null distance (nothing in range): no fire
        assertFalse(TriggerEvaluator.evaluate(p, new TriggerEvaluator.State(0, 0, null)).detonate());
        // within radius: fire
        TriggerEvaluator.Decision d = TriggerEvaluator.evaluate(p, new TriggerEvaluator.State(0, 0, 6.0));
        assertTrue(d.detonate());
        assertEquals(TriggerEvaluator.Trigger.PROXIMITY, d.firedBy());
    }

    @Test
    void proximityPreemptsBeforeDelayElapses() {
        SmartBombParams p = new SmartBombParams(4, 50, null, true, 6);
        TriggerEvaluator.Decision d = TriggerEvaluator.evaluate(p, new TriggerEvaluator.State(1, 0, 3.0));
        assertTrue(d.detonate());
        assertEquals(TriggerEvaluator.Trigger.PROXIMITY, d.firedBy());
    }

    @Test
    void proximityWinsLabelWhenProximityAndTimeCoincide() {
        SmartBombParams p = new SmartBombParams(4, 72000, 6000L, true, 6);
        TriggerEvaluator.Decision d = TriggerEvaluator.evaluate(p, new TriggerEvaluator.State(0, 6000, 2.0));
        assertTrue(d.detonate());
        assertEquals(TriggerEvaluator.Trigger.PROXIMITY, d.firedBy());
    }

    @Test
    void timeWinsLabelWhenTimeAndDelayCoincide() {
        SmartBombParams p = new SmartBombParams(4, 50, 6000L, false, 6);
        TriggerEvaluator.Decision d = TriggerEvaluator.evaluate(p, new TriggerEvaluator.State(50, 6000, null));
        assertTrue(d.detonate());
        assertEquals(TriggerEvaluator.Trigger.TIME, d.firedBy());
    }

    @Test
    void nothingMatchedYieldsNoDetonateAndNone() {
        SmartBombParams p = new SmartBombParams(4, 72000, 6000L, true, 6);
        TriggerEvaluator.Decision d = TriggerEvaluator.evaluate(p, new TriggerEvaluator.State(0, 5000, 10.0));
        assertFalse(d.detonate());
        assertEquals(TriggerEvaluator.Trigger.NONE, d.firedBy());
    }
}
