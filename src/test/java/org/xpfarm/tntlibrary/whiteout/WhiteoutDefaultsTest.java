/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.whiteout;

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
 * Unit tests for {@link WhiteoutDefaults}. Every config is built from an in-memory YAML string with
 * {@link YamlConfiguration#loadConfiguration(java.io.Reader)} -- no server, no plugin instance --
 * exactly like {@code GBombDefaultsTest}, whose {@code RecordingHandler} and {@code newLogger} helpers
 * this test mirrors so it can assert precisely which WARNINGs fire.
 */
final class WhiteoutDefaultsTest {

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

    private static RecordingHandler newLogger(Logger[] out) {
        Logger logger = Logger.getLogger(
                WhiteoutDefaultsTest.class.getName() + "." + LOGGER_SEQ.incrementAndGet());
        logger.setUseParentHandlers(false);
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);
        out[0] = logger;
        return handler;
    }

    private static ConfigurationSection section(String yaml) {
        return YamlConfiguration.loadConfiguration(new StringReader(yaml));
    }

    // (a) fully valid section parses every value with no WARNING
    @Test
    void fullyValidSectionParsesEveryValue() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        WhiteoutDefaults defaults = WhiteoutDefaults.from(section("""
                bombs:
                  whiteout:
                    pull-power: 2.0
                    pull-ticks: 80
                    kill-damage: 500.0
                    effect-ticks: 120
                """), log[0]);

        assertEquals(new WhiteoutDefaults(2.0, 80, 500.0, 120), defaults);
        assertTrue(handler.level(Level.WARNING).isEmpty(), "a valid section must not warn");
    }

    // (b) empty section -> FALLBACK, no WARNING
    @Test
    void absentKeysYieldFallbackWithoutWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        WhiteoutDefaults defaults = WhiteoutDefaults.from(section("{}"), log[0]);

        assertEquals(WhiteoutDefaults.FALLBACK, defaults);
        assertEquals(1.0, defaults.pullPower());
        assertEquals(60, defaults.pullTicks());
        assertEquals(1000.0, defaults.killDamage());
        assertEquals(100, defaults.effectTicks());
        assertTrue(handler.level(Level.WARNING).isEmpty(), "absent keys are not a misconfiguration");
    }

    // (c) a non-numeric double warns + falls back, naming the key
    @Test
    void aNonNumericPullPowerWarnsAndFallsBack() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        WhiteoutDefaults defaults = WhiteoutDefaults.from(section("""
                bombs:
                  whiteout:
                    pull-power: "fast"
                """), log[0]);

        assertEquals(1.0, defaults.pullPower(), "must fall back to the shipped default");
        assertEquals(1, handler.level(Level.WARNING).size());
        assertTrue(handler.level(Level.WARNING).get(0).getMessage()
                .contains("bombs.whiteout.pull-power"), "the WARNING must name the offending key");
    }

    // (d) a fractional pull-ticks warns + falls back (whole-number key)
    @Test
    void aFractionalPullTicksWarnsAndFallsBack() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        WhiteoutDefaults defaults = WhiteoutDefaults.from(section("""
                bombs:
                  whiteout:
                    pull-ticks: 12.5
                """), log[0]);

        assertEquals(60, defaults.pullTicks(), "a fraction is not a whole tick count; falls back");
        assertEquals(1, handler.level(Level.WARNING).size());
        assertTrue(handler.level(Level.WARNING).get(0).getMessage()
                .contains("bombs.whiteout.pull-ticks"));
    }

    // (e) an above-max value is accepted by the reader then clamped by the record, no WARNING
    @Test
    void anAboveMaxPullPowerIsAcceptedThenClampedWithoutWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        WhiteoutDefaults defaults = WhiteoutDefaults.from(section("""
                bombs:
                  whiteout:
                    pull-power: 99
                """), log[0]);

        assertEquals(WhiteoutParams.PULL_POWER_MAX, defaults.pullPower());
        assertEquals(3.0, defaults.pullPower());
        assertTrue(handler.level(Level.WARNING).isEmpty(),
                "an accepted-then-clamped value is normalized, not warned about");
    }

    // (f) a below-min whole number warns + falls back
    @Test
    void aBelowMinEffectTicksWarnsAndFallsBack() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        WhiteoutDefaults defaults = WhiteoutDefaults.from(section("""
                bombs:
                  whiteout:
                    effect-ticks: 0
                """), log[0]);

        assertEquals(100, defaults.effectTicks(), "0 is below the minimum; falls back to 100");
        assertEquals(1, handler.level(Level.WARNING).size());
        assertTrue(handler.level(Level.WARNING).get(0).getMessage()
                .contains("bombs.whiteout.effect-ticks"));
    }

    // (g) the record's compact constructor is the last-line clamp
    @Test
    void aRecordConstructedBelowMinKillDamageIsClampedToOne() {
        assertEquals(1.0, new WhiteoutDefaults(1.0, 60, 0.0, 100).killDamage());
    }

    // null-guards
    @Test
    void nullArgumentsThrowNullPointerException() {
        Logger[] log = new Logger[1];
        newLogger(log);
        assertThrows(NullPointerException.class, () -> WhiteoutDefaults.from(null, log[0]));
        assertThrows(NullPointerException.class, () -> WhiteoutDefaults.from(section("{}"), null));
    }

    // params(...) folds radius from BombSettings with the four config keys
    @Test
    void paramsFoldsRadiusFromBombSettings() {
        WhiteoutDefaults defaults = new WhiteoutDefaults(2.0, 80, 500.0, 120);

        WhiteoutParams params = defaults.params(new BombSettings(true, 24, 100, 0));

        assertEquals(new WhiteoutParams(24, 2.0, 80, 500.0, 120), params);
        assertEquals(24, params.radius(), "radius comes from BombSettings.radius()");
        assertEquals(2.0, params.pullPower());
        assertEquals(80, params.pullTicks());
        assertEquals(500.0, params.killDamage());
        assertEquals(120, params.effectTicks());
    }
}
