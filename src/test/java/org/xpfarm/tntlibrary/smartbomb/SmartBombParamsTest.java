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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Pins the value-type contract: every field is clamped to its documented bound at construction so an
 * out-of-range {@link SmartBombParams} is unrepresentable, and {@code with*} edits produce clamped
 * copies without mutating the original.
 */
final class SmartBombParamsTest {

    @Test
    void defaultIsTheDocumentedBaseline() {
        assertEquals(new SmartBombParams(4, 100, null, false, 6), SmartBombParams.DEFAULT);
    }

    @Test
    void radiusClampsToBounds() {
        assertEquals(SmartBombParams.RADIUS_MIN, new SmartBombParams(0, 100, null, false, 6).radius());
        assertEquals(SmartBombParams.RADIUS_MAX, new SmartBombParams(99, 100, null, false, 6).radius());
    }

    @Test
    void delayClampsToBounds() {
        assertEquals(SmartBombParams.DELAY_MIN, new SmartBombParams(4, 0, null, false, 6).delayTicks());
        assertEquals(
                SmartBombParams.DELAY_MAX, new SmartBombParams(4, 1_000_000, null, false, 6).delayTicks());
    }

    @Test
    void proximityRadiusClampsToBounds() {
        assertEquals(
                SmartBombParams.PROXIMITY_RADIUS_MIN,
                new SmartBombParams(4, 100, null, false, 0).proximityRadius());
        assertEquals(
                SmartBombParams.PROXIMITY_RADIUS_MAX,
                new SmartBombParams(4, 100, null, false, 99).proximityRadius());
    }

    @Test
    void timeTriggerWrapsViaFloorMod() {
        assertEquals(0L, new SmartBombParams(4, 100, 24000L, false, 6).timeTrigger());
        assertEquals(23999L, new SmartBombParams(4, 100, -1L, false, 6).timeTrigger());
        assertEquals(1000L, new SmartBombParams(4, 100, 25000L, false, 6).timeTrigger());
    }

    @Test
    void timeTriggerNullStaysNull() {
        assertNull(new SmartBombParams(4, 100, null, false, 6).timeTrigger());
    }

    @Test
    void withRadiusReturnsClampedCopyLeavingOriginalUnchanged() {
        SmartBombParams original = SmartBombParams.DEFAULT;
        SmartBombParams edited = original.withRadius(99);
        assertEquals(SmartBombParams.RADIUS_MAX, edited.radius());
        assertEquals(4, original.radius());
    }

    @Test
    void withDelayTicksReturnsClampedCopy() {
        SmartBombParams edited = SmartBombParams.DEFAULT.withDelayTicks(0);
        assertEquals(SmartBombParams.DELAY_MIN, edited.delayTicks());
        assertEquals(100, SmartBombParams.DEFAULT.delayTicks());
    }

    @Test
    void withTimeTriggerWrapsAndAcceptsNull() {
        assertEquals(0L, SmartBombParams.DEFAULT.withTimeTrigger(24000L).timeTrigger());
        assertNull(SmartBombParams.DEFAULT.withTimeTrigger(null).timeTrigger());
    }

    @Test
    void withProximityFlipsFlag() {
        assertEquals(true, SmartBombParams.DEFAULT.withProximity(true).proximity());
        assertEquals(false, SmartBombParams.DEFAULT.proximity());
    }

    @Test
    void withProximityRadiusReturnsClampedCopy() {
        SmartBombParams edited = SmartBombParams.DEFAULT.withProximityRadius(99);
        assertEquals(SmartBombParams.PROXIMITY_RADIUS_MAX, edited.proximityRadius());
        assertEquals(6, SmartBombParams.DEFAULT.proximityRadius());
    }
}
