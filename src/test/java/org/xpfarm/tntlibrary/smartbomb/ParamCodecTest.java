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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the codec contract: params survive a serialize/parse round-trip, malformed persisted strings
 * fall back to defaults rather than throwing, and a single command edit validates its key/value with
 * exact operator-facing error text.
 */
final class ParamCodecTest {

    @Test
    void roundTripsEveryRepresentativeShape() {
        List<SmartBombParams> table =
                List.of(
                        SmartBombParams.DEFAULT,
                        new SmartBombParams(1, 1, 0L, true, 1),
                        new SmartBombParams(8, 72000, 23999L, true, 16),
                        new SmartBombParams(4, 100, null, false, 6),
                        new SmartBombParams(5, 200, 6000L, true, 10));
        for (SmartBombParams p : table) {
            assertEquals(p, ParamCodec.parse(ParamCodec.serialize(p)), "round-trip for " + p);
        }
    }

    @Test
    void serializeUsesCanonicalOrderAndEmptyTimeForNull() {
        assertEquals(
                "radius=4,delay=100,time=,proximity=false,proximity-radius=6",
                ParamCodec.serialize(SmartBombParams.DEFAULT));
    }

    @Test
    void serializeWritesTimeNumberWhenPresent() {
        assertEquals(
                "radius=4,delay=100,time=6000,proximity=false,proximity-radius=6",
                ParamCodec.serialize(SmartBombParams.DEFAULT.withTimeTrigger(6000L)));
    }

    @Test
    void parseNullOrBlankYieldsDefault() {
        assertEquals(SmartBombParams.DEFAULT, ParamCodec.parse(null));
        assertEquals(SmartBombParams.DEFAULT, ParamCodec.parse(""));
        assertEquals(SmartBombParams.DEFAULT, ParamCodec.parse("   "));
    }

    @Test
    void parseGarbageYieldsDefault() {
        assertEquals(SmartBombParams.DEFAULT, ParamCodec.parse("garbage"));
    }

    @Test
    void parseKeepsDefaultForGarbageNumericTokens() {
        SmartBombParams parsed = ParamCodec.parse("radius=abc,delay=,time=xyz");
        assertEquals(SmartBombParams.DEFAULT.radius(), parsed.radius());
        assertEquals(SmartBombParams.DEFAULT.delayTicks(), parsed.delayTicks());
        assertNull(parsed.timeTrigger());
    }

    @Test
    void parseIgnoresUnknownKeys() {
        SmartBombParams parsed = ParamCodec.parse("radius=8,bogus=42,proximity=true");
        assertEquals(8, parsed.radius());
        assertTrue(parsed.proximity());
    }

    @Test
    void parseTimeEmptyOrAbsentIsNull() {
        assertNull(ParamCodec.parse("time=").timeTrigger());
        assertNull(ParamCodec.parse("radius=4").timeTrigger());
    }

    @Test
    void applyKeyValueUnknownKeyReturnsError() {
        ParamCodec.Edit edit = ParamCodec.applyKeyValue(SmartBombParams.DEFAULT, "nope", "1");
        assertNull(edit.params());
        assertEquals(
                "Unknown key 'nope'. Valid keys: radius, delay, time, proximity, proximity-radius.",
                edit.error());
    }

    @Test
    void applyKeyValueRadiusSuccess() {
        ParamCodec.Edit edit = ParamCodec.applyKeyValue(SmartBombParams.DEFAULT, "radius", "7");
        assertNull(edit.error());
        assertEquals(7, edit.params().radius());
    }

    @Test
    void applyKeyValueDelaySuccess() {
        ParamCodec.Edit edit = ParamCodec.applyKeyValue(SmartBombParams.DEFAULT, "delay", "500");
        assertNull(edit.error());
        assertEquals(500, edit.params().delayTicks());
    }

    @Test
    void applyKeyValueProximityRadiusSuccess() {
        ParamCodec.Edit edit =
                ParamCodec.applyKeyValue(SmartBombParams.DEFAULT, "proximity-radius", "12");
        assertNull(edit.error());
        assertEquals(12, edit.params().proximityRadius());
    }

    @Test
    void applyKeyValueIntNonNumberReturnsError() {
        ParamCodec.Edit edit = ParamCodec.applyKeyValue(SmartBombParams.DEFAULT, "radius", "big");
        assertNull(edit.params());
        assertEquals("radius must be a whole number.", edit.error());
    }

    @Test
    void applyKeyValueIntOutOfRangeReturnsError() {
        ParamCodec.Edit edit = ParamCodec.applyKeyValue(SmartBombParams.DEFAULT, "radius", "99");
        assertNull(edit.params());
        assertEquals("radius must be between 1 and 8.", edit.error());
    }

    @Test
    void applyKeyValueDelayOutOfRangeUsesItsOwnBounds() {
        ParamCodec.Edit edit = ParamCodec.applyKeyValue(SmartBombParams.DEFAULT, "delay", "0");
        assertNull(edit.params());
        assertEquals("delay must be between 1 and 72000.", edit.error());
    }

    @Test
    void applyKeyValueTimeNumberSuccess() {
        ParamCodec.Edit edit = ParamCodec.applyKeyValue(SmartBombParams.DEFAULT, "time", "6000");
        assertNull(edit.error());
        assertEquals(6000L, edit.params().timeTrigger());
    }

    @Test
    void applyKeyValueTimeOffFormsClearTrigger() {
        for (String raw : new String[] {"off", "none", "", "OFF", "None"}) {
            ParamCodec.Edit edit =
                    ParamCodec.applyKeyValue(SmartBombParams.DEFAULT.withTimeTrigger(500L), "time", raw);
            assertNull(edit.error(), "raw=" + raw);
            assertNull(edit.params().timeTrigger(), "raw=" + raw);
        }
    }

    @Test
    void applyKeyValueTimeOutOfRangeReturnsError() {
        ParamCodec.Edit edit = ParamCodec.applyKeyValue(SmartBombParams.DEFAULT, "time", "24000");
        assertNull(edit.params());
        assertEquals("time must be between 0 and 23999, or 'off'.", edit.error());
    }

    @Test
    void applyKeyValueTimeNonNumberReturnsError() {
        ParamCodec.Edit edit = ParamCodec.applyKeyValue(SmartBombParams.DEFAULT, "time", "noon");
        assertNull(edit.params());
        assertEquals("time must be between 0 and 23999, or 'off'.", edit.error());
    }

    @Test
    void applyKeyValueProximityTrueForms() {
        for (String raw : new String[] {"true", "on", "yes", "1", "TRUE"}) {
            ParamCodec.Edit edit = ParamCodec.applyKeyValue(SmartBombParams.DEFAULT, "proximity", raw);
            assertNull(edit.error(), "raw=" + raw);
            assertTrue(edit.params().proximity(), "raw=" + raw);
        }
    }

    @Test
    void applyKeyValueProximityFalseForms() {
        SmartBombParams on = SmartBombParams.DEFAULT.withProximity(true);
        for (String raw : new String[] {"false", "off", "no", "0", "FALSE"}) {
            ParamCodec.Edit edit = ParamCodec.applyKeyValue(on, "proximity", raw);
            assertNull(edit.error(), "raw=" + raw);
            assertTrue(!edit.params().proximity(), "raw=" + raw);
        }
    }

    @Test
    void applyKeyValueProximityBadValueReturnsError() {
        ParamCodec.Edit edit = ParamCodec.applyKeyValue(SmartBombParams.DEFAULT, "proximity", "maybe");
        assertNull(edit.params());
        assertEquals("proximity must be true or false.", edit.error());
    }

    @Test
    void describeContainsEveryField() {
        String text = ParamCodec.describe(SmartBombParams.DEFAULT);
        assertTrue(text.contains("radius=4"), text);
        assertTrue(text.contains("delay=100"), text);
        assertTrue(text.contains("time-trigger=off"), text);
        assertTrue(text.contains("proximity=off"), text);
        assertTrue(text.contains("proximity-radius=6"), text);
    }

    @Test
    void describeShowsTimeNumberWhenSet() {
        String text = ParamCodec.describe(SmartBombParams.DEFAULT.withTimeTrigger(6000L));
        assertTrue(text.contains("time-trigger=6000"), text);
    }

    @Test
    void canonicalKeyConstantsAreExposed() {
        assertEquals("radius", ParamCodec.KEY_RADIUS);
        assertEquals("delay", ParamCodec.KEY_DELAY);
        assertEquals("time", ParamCodec.KEY_TIME);
        assertEquals("proximity", ParamCodec.KEY_PROXIMITY);
        assertEquals("proximity-radius", ParamCodec.KEY_PROXIMITY_RADIUS);
    }
}
