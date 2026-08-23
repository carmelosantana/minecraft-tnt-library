/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TntLibraryConfig}. All configs are built from an in-memory YAML string with
 * {@link YamlConfiguration#loadConfiguration(java.io.Reader)} -- no server, no plugin instance --
 * exactly as the class javadoc requires. The one exception is {@link
 * #theShippedConfigYmlMatchesTheseDefaults()}, which reads the real {@code
 * src/main/resources/config.yml} off disk on purpose.
 */
final class TntLibraryConfigTest {

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
                TntLibraryConfigTest.class.getName() + "." + LOGGER_SEQ.incrementAndGet());
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
    // Valid, full config
    // ----------------------------------------------------------------------------------------

    @Test
    void fullyValidConfigParsesEveryValue() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        TntLibraryConfig config = TntLibraryConfig.from(section("""
                enabled: true
                bombs:
                  waterbomb:
                    enabled: true
                    radius: 4
                    fuse-ticks: 80
                  twins:
                    enabled: true
                    radius: 3
                    fuse-ticks: 80
                  smartbomb:
                    enabled: true
                    default-radius: 4
                    default-delay-ticks: 100
                  fbomb:
                    enabled: false
                    fuse-ticks: 60
                  gbomb:
                    enabled: false
                    radius: 20
                    fuse-ticks: 60
                    hang-ticks: 50
                  whiteout:
                    enabled: false
                    pull-radius: 24
                    fuse-ticks: 100
                protection:
                  respect-regions: true
                  provider: auto
                resource-pack:
                  url: ''
                  sha1: ''
                  required: false
                """), log[0], PackDefaults.empty());

        assertTrue(config.masterEnabled());

        assertEquals(new BombSettings(true, 4, 80, 0), config.bomb("waterbomb"));
        assertEquals(new BombSettings(true, 3, 80, 0), config.bomb("twins"));
        assertEquals(new BombSettings(true, 4, 100, 0), config.bomb("smartbomb"));
        assertEquals(new BombSettings(false, 0, 60, 0), config.bomb("fbomb"));
        assertEquals(new BombSettings(false, 20, 60, 50), config.bomb("gbomb"));
        assertEquals(new BombSettings(false, 24, 100, 0), config.bomb("whiteout"));

        assertTrue(config.respectRegions());
        assertEquals(ProtectionProvider.AUTO, config.provider());
        assertEquals("", config.resourcePackUrl());
        assertEquals("", config.resourcePackSha1());
        assertFalse(config.resourcePackRequired());
        assertFalse(config.resourcePackConfigured());

        assertTrue(handler.level(Level.WARNING).isEmpty(), "a valid config must not warn");
    }

    @Test
    void resourcePackValuesParseAndConfiguredFlagFollows() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        TntLibraryConfig config = TntLibraryConfig.from(section("""
                resource-pack:
                  url: 'https://example.com/pack.zip'
                  sha1: 'da39a3ee5e6b4b0d3255bfef95601890afd80709'
                  required: true
                """), log[0], PackDefaults.empty());

        assertEquals("https://example.com/pack.zip", config.resourcePackUrl());
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", config.resourcePackSha1());
        assertTrue(config.resourcePackRequired());
        assertTrue(config.resourcePackConfigured());
        assertTrue(handler.level(Level.WARNING).isEmpty());
    }

    // ----------------------------------------------------------------------------------------
    // Missing sections / keys -> documented defaults, no throw
    // ----------------------------------------------------------------------------------------

    @Test
    void anEmptyConfigYieldsEveryShippedDefaultWithoutWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        TntLibraryConfig config = TntLibraryConfig.from(section("{}"), log[0], PackDefaults.empty());

        assertTrue(config.masterEnabled());
        for (BombType type : BombType.values()) {
            assertEquals(type.defaults(), config.bomb(type.id()),
                    "absent bomb " + type.id() + " must take shipped defaults");
        }
        assertTrue(config.respectRegions());
        assertEquals(ProtectionProvider.AUTO, config.provider());
        assertEquals("", config.resourcePackUrl());
        assertEquals("", config.resourcePackSha1());
        assertFalse(config.resourcePackRequired());
        assertTrue(handler.level(Level.WARNING).isEmpty(), "absent keys are not a misconfiguration");
    }

    @Test
    void aPartiallySpecifiedBombKeepsShippedDefaultsForItsAbsentKeys() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        // Only gbomb.radius is set; enabled, fuse-ticks and hang-ticks must keep their defaults.
        TntLibraryConfig config = TntLibraryConfig.from(section("""
                bombs:
                  gbomb:
                    radius: 7
                """), log[0]);

        // gbomb ships disabled, so the master switch does not change enabled here.
        assertEquals(new BombSettings(false, 7, 60, 50), config.bomb("gbomb"));
        assertTrue(handler.level(Level.WARNING).isEmpty());
    }

    // ----------------------------------------------------------------------------------------
    // Bad-typed / out-of-range values -> default + WARNING naming the key, no throw
    // ----------------------------------------------------------------------------------------

    @Test
    void aNonNumericRadiusWarnsAndFallsBack() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        TntLibraryConfig config = TntLibraryConfig.from(section("""
                bombs:
                  waterbomb:
                    radius: "big"
                """), log[0]);

        assertEquals(4, config.bomb("waterbomb").radius(), "must fall back to the shipped default");
        assertEquals(1, handler.level(Level.WARNING).size());
        assertTrue(handler.level(Level.WARNING).get(0).getMessage().contains("bombs.waterbomb.radius"),
                "the WARNING must name the offending key");
    }

    @Test
    void aQuotedBooleanForRespectRegionsWarnsAndFallsBack() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        TntLibraryConfig config = TntLibraryConfig.from(section("""
                protection:
                  respect-regions: "yes"
                """), log[0]);

        assertTrue(config.respectRegions(), "a quoted string is not a boolean; falls back to default true");
        assertEquals(1, handler.level(Level.WARNING).size());
        assertTrue(handler.level(Level.WARNING).get(0).getMessage().contains("protection.respect-regions"));
    }

    @Test
    void anUnknownProviderWarnsAndFallsBackToAuto() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        TntLibraryConfig config = TntLibraryConfig.from(section("""
                protection:
                  provider: banana
                """), log[0]);

        assertEquals(ProtectionProvider.AUTO, config.provider());
        assertEquals(1, handler.level(Level.WARNING).size());
        assertTrue(handler.level(Level.WARNING).get(0).getMessage().contains("protection.provider"));
    }

    @Test
    void aZeroRadiusIsOutOfRangeAndFallsBackWithWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        TntLibraryConfig config = TntLibraryConfig.from(section("""
                bombs:
                  waterbomb:
                    radius: 0
                """), log[0]);

        assertEquals(4, config.bomb("waterbomb").radius());
        assertEquals(1, handler.level(Level.WARNING).size());
        assertTrue(handler.level(Level.WARNING).get(0).getMessage().contains("bombs.waterbomb.radius"));
    }

    @Test
    void aNegativeFuseIsOutOfRangeAndFallsBackWithWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        TntLibraryConfig config = TntLibraryConfig.from(section("""
                bombs:
                  twins:
                    fuse-ticks: -5
                """), log[0]);

        assertEquals(80, config.bomb("twins").fuseTicks());
        assertEquals(1, handler.level(Level.WARNING).size());
        assertTrue(handler.level(Level.WARNING).get(0).getMessage().contains("bombs.twins.fuse-ticks"));
    }

    @Test
    void aWarningQuotesTheOperatorsValueNotBukkitsCoercedOne() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        // 9000000000000000000 wraps to a negative int under Number#intValue(); the raw read must
        // quote the operator's digits, not the wrapped value.
        TntLibraryConfig config = TntLibraryConfig.from(section("""
                bombs:
                  gbomb:
                    radius: 9000000000000000000
                """), log[0]);

        assertEquals(20, config.bomb("gbomb").radius());
        String message = handler.level(Level.WARNING).get(0).getMessage();
        assertTrue(message.contains("9000000000000000000"), message);
        assertFalse(message.contains("-"), "must not quote a wrapped negative value: " + message);
    }

    @Test
    void oneInvalidBombDoesNotDiscardTheOthers() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        TntLibraryConfig config = TntLibraryConfig.from(section("""
                bombs:
                  waterbomb:
                    radius: "big"
                  twins:
                    radius: 9
                """), log[0]);

        assertEquals(4, config.bomb("waterbomb").radius(), "the bad bomb falls back");
        assertEquals(9, config.bomb("twins").radius(), "a valid neighbour survives");
        assertEquals(1, handler.level(Level.WARNING).size());
    }

    // ----------------------------------------------------------------------------------------
    // Master switch interaction
    // ----------------------------------------------------------------------------------------

    @Test
    void masterDisabledForcesEveryBombDisabledButKeepsMagnitudes() {
        Logger[] log = new Logger[1];
        newLogger(log);

        TntLibraryConfig config = TntLibraryConfig.from(section("""
                enabled: false
                bombs:
                  waterbomb:
                    enabled: true
                    radius: 4
                    fuse-ticks: 80
                """), log[0]);

        assertFalse(config.masterEnabled());
        BombSettings waterbomb = config.bomb("waterbomb");
        assertFalse(waterbomb.enabled(), "master off must force every bomb disabled");
        assertEquals(4, waterbomb.radius(), "magnitudes survive the master gate");
        assertEquals(80, waterbomb.fuseTicks());
    }

    @Test
    void masterEnabledLeavesPerBombEnabledUntouched() {
        Logger[] log = new Logger[1];
        newLogger(log);

        TntLibraryConfig config = TntLibraryConfig.from(section("""
                enabled: true
                bombs:
                  waterbomb:
                    enabled: true
                  fbomb:
                    enabled: false
                """), log[0]);

        assertTrue(config.bomb("waterbomb").enabled());
        assertFalse(config.bomb("fbomb").enabled(), "a per-bomb false stays false under an enabled master");
    }

    @Test
    void canonicalConstructorCannotProduceABombEnabledUnderADisabledMaster() {
        // Even a hand-built instance passing an enabled bomb under masterEnabled=false must gate it.
        java.util.Map<String, BombSettings> lying =
                java.util.Map.of("waterbomb", new BombSettings(true, 4, 80, 0));

        TntLibraryConfig config = new TntLibraryConfig(
                false, lying, true, ProtectionProvider.AUTO, "", "", false, "", false);

        assertFalse(config.bomb("waterbomb").enabled(),
                "the canonical constructor must re-derive bomb enabled from the master switch");
        assertEquals(4, config.bomb("waterbomb").radius());
    }

    @Test
    void theBombMapIsUnmodifiable() {
        Logger[] log = new Logger[1];
        newLogger(log);
        TntLibraryConfig config = TntLibraryConfig.from(section("{}"), log[0]);
        assertThrows(UnsupportedOperationException.class,
                () -> config.bombs().put("x", BombSettings.DISABLED));
    }

    // ----------------------------------------------------------------------------------------
    // Provider case-insensitivity
    // ----------------------------------------------------------------------------------------

    @Test
    void providerParsesCaseInsensitively() {
        for (String token : List.of("auto", "AUTO", "Auto", "worldguard", "WorldGuard", "WORLDGUARD",
                "griefprevention", "GriefPrevention", "none", "NONE")) {
            Logger[] log = new Logger[1];
            RecordingHandler handler = newLogger(log);

            TntLibraryConfig config = TntLibraryConfig.from(section("""
                    protection:
                      provider: %s
                    """.formatted(token)), log[0]);

            assertEquals(ProtectionProvider.valueOf(token.toUpperCase(Locale.ROOT)), config.provider(), token);
            assertTrue(handler.level(Level.WARNING).isEmpty(), token);
        }
    }

    @Test
    void aBlankProviderTakesAutoSilently() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        TntLibraryConfig config = TntLibraryConfig.from(section("""
                protection:
                  provider: ''
                """), log[0]);

        assertEquals(ProtectionProvider.AUTO, config.provider());
        assertTrue(handler.level(Level.WARNING).isEmpty(), "a blank provider is 'left at default', not a mistake");
    }

    // ----------------------------------------------------------------------------------------
    // Lookups and null-guards
    // ----------------------------------------------------------------------------------------

    @Test
    void anUnknownBombIdYieldsTheDisabledDefault() {
        Logger[] log = new Logger[1];
        newLogger(log);
        TntLibraryConfig config = TntLibraryConfig.from(section("{}"), log[0]);
        assertEquals(BombSettings.DISABLED, config.bomb("no-such-bomb"));
    }

    @Test
    void nullArgumentsThrowNullPointerException() {
        Logger[] log = new Logger[1];
        newLogger(log);

        NullPointerException nullConfig = assertThrows(
                NullPointerException.class, () -> TntLibraryConfig.from(null, log[0]));
        assertEquals("config", nullConfig.getMessage());

        NullPointerException nullLogger = assertThrows(
                NullPointerException.class, () -> TntLibraryConfig.from(section("{}"), null));
        assertEquals("logger", nullLogger.getMessage());
    }

    // ----------------------------------------------------------------------------------------
    // Never-throw discipline
    // ----------------------------------------------------------------------------------------

    @Test
    void noHostileValueEverThrows() {
        List<String> hostile = List.of(
                "{}",
                "enabled: banana\n",
                "enabled:\n  nested: true\n",
                "bombs: false\n",
                "bombs:\n  waterbomb: false\n",
                "bombs:\n  waterbomb:\n    radius: [1, 2]\n",
                "bombs:\n  waterbomb:\n    radius:\n      nested: 1\n",
                "bombs:\n  waterbomb:\n    radius: .inf\n",
                "bombs:\n  waterbomb:\n    radius: .nan\n",
                "bombs:\n  waterbomb:\n    fuse-ticks: 9000000000000000000\n",
                "bombs:\n  gbomb:\n    hang-ticks: banana\n",
                "protection: false\n",
                "protection:\n  provider: [a, b]\n",
                "protection:\n  provider:\n    nested: 1\n",
                "protection:\n  respect-regions: 0\n",
                "resource-pack: false\n",
                "resource-pack:\n  url: 5\n",
                "resource-pack:\n  required: maybe\n");

        for (String yaml : hostile) {
            Logger[] log = new Logger[1];
            RecordingHandler handler = newLogger(log);
            TntLibraryConfig config =
                    assertDoesNotThrow(() -> TntLibraryConfig.from(section(yaml), log[0]), yaml);
            assertNotNull(config, yaml);
            for (LogRecord record : handler.level(Level.WARNING)) {
                assertFalse(record.getMessage().contains("MemorySection"),
                        "a Bukkit internal toString must never reach an operator: " + record.getMessage());
            }
        }
    }

    // ----------------------------------------------------------------------------------------
    // The shipped config.yml must agree with BombType's defaults
    // ----------------------------------------------------------------------------------------

    /**
     * Pins the shipped {@code config.yml} against {@link BombType#defaults()} and the other shipped
     * defaults. A fresh install reads its values from this YAML (via {@code saveDefaultConfig()}),
     * while a server upgraded from an earlier version reads them from the constants in this package,
     * so any drift between the two would split behaviour across servers running the same JAR.
     *
     * <p>{@code loadConfiguration(Reader)} is used rather than {@code setDefaults}, so the shipped
     * YAML is the only source in play. Zero WARNINGs is asserted alongside the value checks, because
     * a value that is invalid rather than merely different would fall back to its default and satisfy
     * an equality check on its own.
     */
    @Test
    void theShippedConfigYmlMatchesTheseDefaults() throws IOException {
        Path shipped = Path.of("src", "main", "resources", "config.yml");
        assertTrue(Files.exists(shipped), "shipped config.yml is missing: " + shipped);

        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        YamlConfiguration shippedConfig;
        try (Reader reader = Files.newBufferedReader(shipped)) {
            shippedConfig = YamlConfiguration.loadConfiguration(reader);
        }

        // Isolate the shipped config.yml from whatever pack hash this build baked into
        // PackDefaults: a released build bakes a real URL/SHA-1, which would make the shipped
        // (empty) resource-pack section resolve to a *configured* pack and, for a config.yml sha1
        // that differed from the baked one, warn about staleness. This test is about config.yml's
        // own operator-facing defaults, so it pins an empty built-in fallback; the baked-fallback
        // resolution has its own coverage in ResourcePackResolutionTest.
        TntLibraryConfig config = TntLibraryConfig.from(shippedConfig, log[0], PackDefaults.empty());

        assertTrue(config.masterEnabled(), "shipped enabled must be true");
        for (BombType type : BombType.values()) {
            assertEquals(type.defaults(), config.bomb(type.id()),
                    "shipped config.yml value for " + type.id() + " must equal BombType.defaults()");
        }
        assertTrue(config.respectRegions());
        assertEquals(ProtectionProvider.AUTO, config.provider());
        assertFalse(config.resourcePackConfigured(), "shipped resource-pack url/sha1 are empty");
        assertFalse(config.resourcePackRequired());
        assertTrue(handler.level(Level.WARNING).isEmpty(),
                "the shipped config.yml must not contain a value this plugin warns about");
    }
}
