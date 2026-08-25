/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.gbomb;

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
import org.xpfarm.tntlibrary.config.BombSettings;

/**
 * Unit tests for {@link GBombDefaults}. Every config is built from an in-memory YAML string with
 * {@link YamlConfiguration#loadConfiguration(java.io.Reader)} -- no server, no plugin instance --
 * exactly like {@code SmartBombDefaultsTest}, whose {@code RecordingHandler} and {@code newLogger}
 * helpers this test mirrors so it can assert precisely which WARNINGs fire.
 */
final class GBombDefaultsTest {

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
                GBombDefaultsTest.class.getName() + "." + LOGGER_SEQ.incrementAndGet());
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

        GBombDefaults defaults = GBombDefaults.from(section("""
                bombs:
                  gbomb:
                    launch-power: 2.0
                    kill-damage: 500.0
                """), log[0]);

        assertEquals(new GBombDefaults(2.0, 500.0), defaults);
        assertTrue(handler.level(Level.WARNING).isEmpty(), "a valid section must not warn");
    }

    // ----------------------------------------------------------------------------------------
    // Absent keys -> FALLBACK, no warning
    // ----------------------------------------------------------------------------------------

    @Test
    void absentKeysYieldFallbackWithoutWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        GBombDefaults defaults = GBombDefaults.from(section("{}"), log[0]);

        assertEquals(GBombDefaults.FALLBACK, defaults);
        assertEquals(1.2, defaults.launchPower());
        assertEquals(1000.0, defaults.killDamage());
        assertTrue(handler.level(Level.WARNING).isEmpty(), "absent keys are not a misconfiguration");
    }

    // ----------------------------------------------------------------------------------------
    // Present-but-invalid -> FALLBACK + WARNING naming the key
    // ----------------------------------------------------------------------------------------

    @Test
    void aNonNumericLaunchPowerWarnsAndFallsBack() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        GBombDefaults defaults = GBombDefaults.from(section("""
                bombs:
                  gbomb:
                    launch-power: "fast"
                """), log[0]);

        assertEquals(1.2, defaults.launchPower(), "must fall back to the shipped default");
        assertEquals(1, handler.level(Level.WARNING).size());
        assertTrue(handler.level(Level.WARNING).get(0).getMessage()
                .contains("bombs.gbomb.launch-power"), "the WARNING must name the offending key");
    }

    // ----------------------------------------------------------------------------------------
    // In-range fractions accepted; out-of-range accepted-then-clamped without warning
    // ----------------------------------------------------------------------------------------

    @Test
    void aFractionalLaunchPowerIsAcceptedWithoutWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        GBombDefaults defaults = GBombDefaults.from(section("""
                bombs:
                  gbomb:
                    launch-power: 2.5
                """), log[0]);

        assertEquals(2.5, defaults.launchPower(), "a fraction is a valid launch power");
        assertTrue(handler.level(Level.WARNING).isEmpty());
    }

    @Test
    void anAboveMaxLaunchPowerIsAcceptedThenClampedByTheRecordWithoutWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        // 99 is a valid finite number >= 0, so the reader accepts it silently; the record's compact
        // constructor then clamps it to LAUNCH_POWER_MAX (3.0). No warning fires -- normalization.
        GBombDefaults defaults = GBombDefaults.from(section("""
                bombs:
                  gbomb:
                    launch-power: 99
                """), log[0]);

        assertEquals(GBombParams.LAUNCH_POWER_MAX, defaults.launchPower());
        assertEquals(3.0, defaults.launchPower());
        assertTrue(handler.level(Level.WARNING).isEmpty(),
                "an accepted-then-clamped value is normalized, not warned about");
    }

    @Test
    void aBelowMinKillDamageIsAcceptedThenClampedByTheRecordWithoutWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        // 0 fails the reader's ">= 1" validation, so it falls back to 1000 and warns.
        GBombDefaults defaults = GBombDefaults.from(section("""
                bombs:
                  gbomb:
                    kill-damage: 0
                """), log[0]);

        assertEquals(1000.0, defaults.killDamage(), "0 is below the minimum; falls back to 1000");
        assertEquals(1, handler.level(Level.WARNING).size());
        assertTrue(handler.level(Level.WARNING).get(0).getMessage()
                .contains("bombs.gbomb.kill-damage"), "the WARNING must name the offending key");
    }

    @Test
    void aRecordConstructedBelowMinKillDamageIsClampedToOne() {
        // The record's compact constructor is the last-line clamp: KILL_DAMAGE_MIN (1.0).
        assertEquals(1.0, new GBombDefaults(1.2, 0.0).killDamage());
    }

    // ----------------------------------------------------------------------------------------
    // Null-guards
    // ----------------------------------------------------------------------------------------

    @Test
    void nullArgumentsThrowNullPointerException() {
        Logger[] log = new Logger[1];
        newLogger(log);

        assertThrows(NullPointerException.class, () -> GBombDefaults.from(null, log[0]));
        assertThrows(NullPointerException.class, () -> GBombDefaults.from(section("{}"), null));
    }

    // ----------------------------------------------------------------------------------------
    // params(...) folds radius/hangTicks from BombSettings with config launch/kill
    // ----------------------------------------------------------------------------------------

    @Test
    void paramsFoldsRadiusAndHangTicksFromBombSettings() {
        GBombDefaults defaults = new GBombDefaults(2.0, 500.0);

        GBombParams params = defaults.params(new BombSettings(true, 20, 60, 50));

        assertEquals(new GBombParams(20, 50, 2.0, 500.0), params);
        assertEquals(20, params.radius(), "radius comes from BombSettings.radius()");
        assertEquals(50, params.hangTicks(), "hangTicks comes from BombSettings.hangTicks()");
        assertEquals(2.0, params.launchPower(), "launchPower comes from this record");
        assertEquals(500.0, params.killDamage(), "killDamage comes from this record");
    }
}
