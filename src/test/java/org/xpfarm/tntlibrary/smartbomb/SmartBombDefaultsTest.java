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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SmartBombDefaults}. Every config is built from an in-memory YAML string with
 * {@link YamlConfiguration#loadConfiguration(java.io.Reader)} -- no server, no plugin instance --
 * exactly like {@code TntLibraryConfigTest}, whose {@code RecordingHandler} and {@code newLogger}
 * helpers this test mirrors so it can assert precisely which WARNINGs fire.
 */
final class SmartBombDefaultsTest {

    /** Records every emitted {@link LogRecord} so tests can assert exactly what was warned. */
    private static final class RecordingHandler extends Handler {
        final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        List<LogRecord> level(Level level) {
            return records.stream().filter(r -> r.getLevel().equals(level)).toList();
        }
    }

    private static final AtomicInteger LOGGER_SEQ = new AtomicInteger();

    /** A fresh, non-parent-propagating logger with a fresh recording handler attached. */
    private static RecordingHandler newLogger(Logger[] out) {
        Logger logger = Logger.getLogger(
                SmartBombDefaultsTest.class.getName() + "." + LOGGER_SEQ.incrementAndGet());
        logger.setUseParentHandlers(false);
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);
        out[0] = logger;
        return handler;
    }

    private static ConfigurationSection section(String yaml) {
        return YamlConfiguration.loadConfiguration(new StringReader(yaml));
    }

    // ----------------------------------------------------------------------------------------
    // Valid, full section
    // ----------------------------------------------------------------------------------------

    @Test
    void fullyValidSectionParsesEveryValue() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        SmartBombDefaults defaults = SmartBombDefaults.from(section("""
                bombs:
                  smartbomb:
                    default-proximity-radius: 8
                    proximity-default: true
                    time-trigger-default: true
                """), log[0]);

        assertEquals(new SmartBombDefaults(8, true, true), defaults);
        assertTrue(handler.level(Level.WARNING).isEmpty(), "a valid section must not warn");
    }

    // ----------------------------------------------------------------------------------------
    // Absent keys -> FALLBACK, no warning
    // ----------------------------------------------------------------------------------------

    @Test
    void absentKeysYieldFallbackWithoutWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        SmartBombDefaults defaults = SmartBombDefaults.from(section("{}"), log[0]);

        assertEquals(SmartBombDefaults.FALLBACK, defaults);
        assertEquals(6, defaults.proximityRadius());
        assertFalse(defaults.proximityDefault());
        assertFalse(defaults.timeTriggerDefault());
        assertTrue(handler.level(Level.WARNING).isEmpty(), "absent keys are not a misconfiguration");
    }

    // ----------------------------------------------------------------------------------------
    // Out-of-range default-proximity-radius: two distinct behaviours
    // ----------------------------------------------------------------------------------------

    @Test
    void aZeroProximityRadiusIsBelowTheMinimumAndFallsBackWithWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        // 0 fails the reader's ">= 1" validation, so it falls back to 6 and warns -- it is never
        // handed to the record to be clamped.
        SmartBombDefaults defaults = SmartBombDefaults.from(section("""
                bombs:
                  smartbomb:
                    default-proximity-radius: 0
                """), log[0]);

        assertEquals(6, defaults.proximityRadius(), "0 is below the minimum; falls back to 6");
        assertEquals(1, handler.level(Level.WARNING).size());
        assertTrue(handler.level(Level.WARNING).get(0).getMessage()
                        .contains("bombs.smartbomb.default-proximity-radius"),
                "the WARNING must name the offending key");
    }

    @Test
    void anAboveMaxProximityRadiusIsAcceptedThenClampedByTheRecordWithoutWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        // 99 is a valid whole number >= 1, so the reader accepts it silently; the record's compact
        // constructor then clamps it to PROX_MAX (16). No warning fires -- this is normalization,
        // not a misconfiguration.
        SmartBombDefaults defaults = SmartBombDefaults.from(section("""
                bombs:
                  smartbomb:
                    default-proximity-radius: 99
                """), log[0]);

        assertEquals(SmartBombParams.PROXIMITY_RADIUS_MAX, defaults.proximityRadius());
        assertEquals(16, defaults.proximityRadius());
        assertTrue(handler.level(Level.WARNING).isEmpty(),
                "an accepted-then-clamped value is normalized, not warned about");
    }

    // ----------------------------------------------------------------------------------------
    // Bad-typed values -> FALLBACK + WARNING naming the key
    // ----------------------------------------------------------------------------------------

    @Test
    void aNonNumericProximityRadiusWarnsAndFallsBack() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        SmartBombDefaults defaults = SmartBombDefaults.from(section("""
                bombs:
                  smartbomb:
                    default-proximity-radius: "abc"
                """), log[0]);

        assertEquals(6, defaults.proximityRadius(), "must fall back to the shipped default");
        assertEquals(1, handler.level(Level.WARNING).size());
        assertTrue(handler.level(Level.WARNING).get(0).getMessage()
                .contains("bombs.smartbomb.default-proximity-radius"));
    }

    @Test
    void aQuotedBooleanForProximityDefaultWarnsAndFallsBack() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        // "yes" as a quoted string is not a YAML boolean.
        SmartBombDefaults defaults = SmartBombDefaults.from(section("""
                bombs:
                  smartbomb:
                    proximity-default: "yes"
                """), log[0]);

        assertFalse(defaults.proximityDefault(), "a quoted string is not a boolean; falls back to false");
        assertEquals(1, handler.level(Level.WARNING).size());
        assertTrue(handler.level(Level.WARNING).get(0).getMessage()
                .contains("bombs.smartbomb.proximity-default"));
    }

    @Test
    void aRealYamlBooleanForProximityDefaultIsAcceptedWithoutWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        SmartBombDefaults defaults = SmartBombDefaults.from(section("""
                bombs:
                  smartbomb:
                    proximity-default: true
                """), log[0]);

        assertTrue(defaults.proximityDefault());
        assertTrue(handler.level(Level.WARNING).isEmpty());
    }

    // ----------------------------------------------------------------------------------------
    // Null-guards
    // ----------------------------------------------------------------------------------------

    @Test
    void nullArgumentsThrowNullPointerException() {
        Logger[] log = new Logger[1];
        newLogger(log);

        assertThrows(NullPointerException.class, () -> SmartBombDefaults.from(null, log[0]));
        assertThrows(NullPointerException.class, () -> SmartBombDefaults.from(section("{}"), null));
    }

    // ----------------------------------------------------------------------------------------
    // seed(...) builds the SmartBombParams a freshly placed bomb starts with
    // ----------------------------------------------------------------------------------------

    @Test
    void seedBuildsParamsFromFallbackWithNoTimeTrigger() {
        SmartBombParams seeded = SmartBombDefaults.FALLBACK.seed(4, 100);

        assertEquals(new SmartBombParams(4, 100, null, false, 6), seeded);
        assertNull(seeded.timeTrigger(), "a freshly placed bomb's time trigger is off");
    }

    @Test
    void seedReClampsOutOfRangeRadiusAndDelayViaTheParamsConstructor() {
        // 99 radius clamps to RADIUS_MAX (8); 0 delay clamps up to DELAY_MIN (1).
        SmartBombParams seeded = SmartBombDefaults.FALLBACK.seed(99, 0);

        assertEquals(8, seeded.radius());
        assertEquals(1, seeded.delayTicks());
        assertNull(seeded.timeTrigger());
        assertFalse(seeded.proximity());
        assertEquals(6, seeded.proximityRadius());
    }

    @Test
    void seedCarriesTheProximityDefaultsFromTheRecord() {
        SmartBombDefaults defaults = new SmartBombDefaults(10, true, true);
        SmartBombParams seeded = defaults.seed(4, 100);

        assertTrue(seeded.proximity(), "seed carries proximityDefault");
        assertEquals(10, seeded.proximityRadius(), "seed carries proximityRadius");
        assertNull(seeded.timeTrigger(), "timeTriggerDefault never seeds a time value");
    }
}
