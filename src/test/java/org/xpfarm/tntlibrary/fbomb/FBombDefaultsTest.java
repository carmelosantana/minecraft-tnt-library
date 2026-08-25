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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Unit tests for {@link FBombDefaults}. Every config is built from an in-memory YAML string with
 * {@link YamlConfiguration#loadConfiguration(java.io.Reader)} -- no server, no plugin instance --
 * mirroring {@code SmartBombDefaultsTest}'s {@code RecordingHandler} and {@code newLogger} helpers
 * so it can assert precisely which WARNINGs fire.
 */
final class FBombDefaultsTest {

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
                FBombDefaultsTest.class.getName() + "." + LOGGER_SEQ.incrementAndGet());
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

        FBombDefaults defaults = FBombDefaults.from(section("""
                bombs:
                  fbomb:
                    menace-ticks: 100
                    spawn-distance: 10
                    spawn-height: 4
                    bossbar-range: 64
                    skull-count: 12
                    skull-cadence-ticks: 15
                """), log[0]);

        assertEquals(new FBombDefaults(100, 10, 4, 64, 12, 15), defaults);
        assertTrue(handler.level(Level.WARNING).isEmpty(), "a valid section must not warn");
    }

    // ----------------------------------------------------------------------------------------
    // Absent keys -> FALLBACK, no warning
    // ----------------------------------------------------------------------------------------

    @Test
    void absentKeysYieldFallbackWithoutWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        FBombDefaults defaults = FBombDefaults.from(section("{}"), log[0]);

        assertEquals(FBombDefaults.FALLBACK, defaults);
        assertEquals(60, defaults.menaceTicks());
        assertEquals(12, defaults.spawnDistance());
        assertEquals(6, defaults.spawnHeight());
        assertEquals(48, defaults.bossbarRange());
        assertEquals(6, defaults.skullCount());
        assertEquals(8, defaults.skullCadenceTicks());
        assertTrue(handler.level(Level.WARNING).isEmpty(), "absent keys are not a misconfiguration");
    }

    // ----------------------------------------------------------------------------------------
    // Below-minimum whole number -> falls back + WARNING naming the key
    // ----------------------------------------------------------------------------------------

    @Test
    void aBelowMinimumSpawnDistanceFallsBackWithWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        // 1 fails the reader's ">= 2" validation, so it falls back to 12 and warns -- it is never
        // handed to the record to be clamped.
        FBombDefaults defaults = FBombDefaults.from(section("""
                bombs:
                  fbomb:
                    spawn-distance: 1
                """), log[0]);

        assertEquals(12, defaults.spawnDistance(), "1 is below the minimum; falls back to 12");
        assertEquals(1, handler.level(Level.WARNING).size());
        assertTrue(handler.level(Level.WARNING).get(0).getMessage()
                        .contains("bombs.fbomb.spawn-distance"),
                "the WARNING must name the offending key");
    }

    // ----------------------------------------------------------------------------------------
    // Above-max value -> accepted by the reader, clamped by the record, no warning
    // ----------------------------------------------------------------------------------------

    @Test
    void anAboveMaxMenaceTicksIsAcceptedThenClampedByTheRecordWithoutWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        // 99999 is a valid whole number >= 20, so the reader accepts it silently; the record's
        // compact constructor then clamps it to MENACE_TICKS_MAX (1200). No warning fires -- this
        // is normalization, not a misconfiguration.
        FBombDefaults defaults = FBombDefaults.from(section("""
                bombs:
                  fbomb:
                    menace-ticks: 99999
                """), log[0]);

        assertEquals(FBombParams.MENACE_TICKS_MAX, defaults.menaceTicks());
        assertEquals(1200, defaults.menaceTicks());
        assertTrue(handler.level(Level.WARNING).isEmpty(),
                "an accepted-then-clamped value is normalized, not warned about");
    }

    // ----------------------------------------------------------------------------------------
    // Non-numeric value -> WARNING + falls back
    // ----------------------------------------------------------------------------------------

    @Test
    void aNonNumericSkullCountWarnsAndFallsBack() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        FBombDefaults defaults = FBombDefaults.from(section("""
                bombs:
                  fbomb:
                    skull-count: "abc"
                """), log[0]);

        assertEquals(6, defaults.skullCount(), "must fall back to the shipped default");
        assertEquals(1, handler.level(Level.WARNING).size());
        assertTrue(handler.level(Level.WARNING).get(0).getMessage()
                .contains("bombs.fbomb.skull-count"));
    }

    // ----------------------------------------------------------------------------------------
    // Null-guards
    // ----------------------------------------------------------------------------------------

    @Test
    void nullArgumentsThrowNullPointerException() {
        Logger[] log = new Logger[1];
        newLogger(log);

        assertThrows(NullPointerException.class, () -> FBombDefaults.from(null, log[0]));
        assertThrows(NullPointerException.class, () -> FBombDefaults.from(section("{}"), null));
    }

    // ----------------------------------------------------------------------------------------
    // seed(...) builds the FBombParams a freshly placed bomb starts with
    // ----------------------------------------------------------------------------------------

    @Test
    void seedReClampsOutOfRangeRadiusAndFuseViaTheParamsConstructorAndCarriesFallbackKeys() {
        // 99 radius clamps to RADIUS_MAX (8); 0 fuse clamps up to FUSE_TICKS_MIN (1).
        FBombParams seeded = FBombDefaults.FALLBACK.seed(99, 0);

        assertEquals(8, seeded.radius());
        assertEquals(1, seeded.fuseTicks());
        assertEquals(60, seeded.menaceTicks());
        assertEquals(12, seeded.spawnDistance());
        assertEquals(6, seeded.spawnHeight());
        assertEquals(48, seeded.bossbarRange());
        assertEquals(6, seeded.skullCount());
        assertEquals(8, seeded.skullCadenceTicks());
    }

    @Test
    void seedCarriesTheSixKeysFromACustomRecord() {
        FBombDefaults defaults = new FBombDefaults(100, 10, 4, 64, 12, 15);
        FBombParams seeded = defaults.seed(4, 60);

        assertEquals(4, seeded.radius());
        assertEquals(60, seeded.fuseTicks());
        assertEquals(100, seeded.menaceTicks());
        assertEquals(10, seeded.spawnDistance());
        assertEquals(4, seeded.spawnHeight());
        assertEquals(64, seeded.bossbarRange());
        assertEquals(12, seeded.skullCount());
        assertEquals(15, seeded.skullCadenceTicks());
    }
}
