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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
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
 * Unit tests for the {@code resource-pack} resolution order in {@link TntLibraryConfig}: config value
 * wins, else the baked {@link PackDefaults}, else unconfigured -- plus the partial-override refusal,
 * the stale-pin warning, and URL/SHA-1 validation. All configs are built from an in-memory YAML
 * string with {@link YamlConfiguration#loadConfiguration(java.io.Reader)} -- no server, no plugin
 * instance -- and every fallback is supplied explicitly via {@link PackDefaults} so nothing depends
 * on Maven's resource filtering having run. Mirrors the sibling RedstoneStuff plugin's
 * {@code ConfigValidationTest}.
 */
final class ResourcePackResolutionTest {

    private static final String VALID_SHA1 = "da39a3ee5e6b4b0d3255bfef95601890afd80709";
    private static final String BUILT_IN_SHA1 = "0123456789abcdef0123456789abcdef01234567";

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

    private static RecordingHandler newLogger(Logger[] out) {
        Logger logger = Logger.getLogger(
                ResourcePackResolutionTest.class.getName() + "." + LOGGER_SEQ.incrementAndGet());
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
    // Validation of a fully-specified operator pack
    // ----------------------------------------------------------------------------------------

    @Test
    void fullyValidConfigEnablesPackDelivery() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        TntLibraryConfig result = TntLibraryConfig.from(section("""
                resource-pack:
                  url: 'https://example.com/pack.zip'
                  sha1: '%s'
                  required: false
                """.formatted(VALID_SHA1)), log[0], PackDefaults.empty());

        assertTrue(result.packDeliveryEnabled());
        assertEquals("https://example.com/pack.zip", result.resourcePackUrl());
        assertEquals(VALID_SHA1, result.resourcePackSha1());
        assertTrue(handler.level(Level.WARNING).isEmpty(), "no warnings expected for a valid config");
    }

    @Test
    void httpUrlIsRefused() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        TntLibraryConfig result = TntLibraryConfig.from(section("""
                resource-pack:
                  url: 'http://example.com/pack.zip'
                  sha1: '%s'
                """.formatted(VALID_SHA1)), log[0], PackDefaults.empty());

        assertFalse(result.packDeliveryEnabled());
        assertEquals(1, handler.level(Level.WARNING).size());
        assertTrue(handler.level(Level.WARNING).get(0).getMessage().contains("http"));
    }

    @Test
    void malformedUrlIsRefused() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        TntLibraryConfig result = TntLibraryConfig.from(section("""
                resource-pack:
                  url: 'https://exa mple.com/pack zip file.zip'
                  sha1: '%s'
                """.formatted(VALID_SHA1)), log[0], PackDefaults.empty());

        assertFalse(result.packDeliveryEnabled());
        assertEquals(1, handler.level(Level.WARNING).size());
    }

    @Test
    void uppercaseHexSha1IsRefused() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        TntLibraryConfig result = TntLibraryConfig.from(section("""
                resource-pack:
                  url: 'https://example.com/pack.zip'
                  sha1: '%s'
                """.formatted(VALID_SHA1.toUpperCase(Locale.ROOT))), log[0], PackDefaults.empty());

        assertFalse(result.packDeliveryEnabled());
        assertEquals(1, handler.level(Level.WARNING).size());
        assertTrue(handler.level(Level.WARNING).get(0).getMessage().toLowerCase(Locale.ROOT)
                .contains("uppercase"));
    }

    @Test
    void thirtyNineCharacterSha1IsRefused() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        TntLibraryConfig result = TntLibraryConfig.from(section("""
                resource-pack:
                  url: 'https://example.com/pack.zip'
                  sha1: '%s'
                """.formatted(VALID_SHA1.substring(0, 39))), log[0], PackDefaults.empty());

        assertFalse(result.packDeliveryEnabled());
        assertEquals(1, handler.level(Level.WARNING).size());
    }

    @Test
    void fortyOneCharacterSha1IsRefused() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        TntLibraryConfig result = TntLibraryConfig.from(section("""
                resource-pack:
                  url: 'https://example.com/pack.zip'
                  sha1: '%s'
                """.formatted(VALID_SHA1 + "a")), log[0], PackDefaults.empty());

        assertFalse(result.packDeliveryEnabled());
        assertEquals(1, handler.level(Level.WARNING).size());
    }

    @Test
    void nonHexSha1IsRefused() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        TntLibraryConfig result = TntLibraryConfig.from(section("""
                resource-pack:
                  url: 'https://example.com/pack.zip'
                  sha1: 'zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz'
                """), log[0], PackDefaults.empty());

        assertFalse(result.packDeliveryEnabled());
        assertEquals(1, handler.level(Level.WARNING).size());
    }

    // ----------------------------------------------------------------------------------------
    // Unconfigured: empty config + empty built-in default
    // ----------------------------------------------------------------------------------------

    @Test
    void emptyUrlAndSha1WithNoBuiltInDefaultsDisableDeliveryViaInfoNotWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        TntLibraryConfig result = TntLibraryConfig.from(section("""
                resource-pack:
                  url: ''
                  sha1: ''
                """), log[0], PackDefaults.empty());

        assertFalse(result.packDeliveryEnabled());
        assertTrue(handler.level(Level.WARNING).isEmpty(), "empty values must not warn");
        assertEquals(1, handler.level(Level.INFO).size());
    }

    // ----------------------------------------------------------------------------------------
    // Baked-default fallback and the config-wins rule
    // ----------------------------------------------------------------------------------------

    @Test
    void emptyConfigFallsBackToBuiltInDefaultsAndEnablesDelivery() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        PackDefaults defaults = PackDefaults.of("https://example.com/built-in-pack.zip", BUILT_IN_SHA1);
        TntLibraryConfig result = TntLibraryConfig.from(section("""
                resource-pack:
                  url: ''
                  sha1: ''
                """), log[0], defaults);

        assertTrue(result.packDeliveryEnabled(), "built-in values must be used when config is empty");
        assertEquals("https://example.com/built-in-pack.zip", result.resourcePackUrl());
        assertEquals(BUILT_IN_SHA1, result.resourcePackSha1());
        assertTrue(handler.level(Level.WARNING).isEmpty(), "a built-in default is not itself stale");
    }

    @Test
    void nonEmptyConfigValuesWinOverBuiltInDefaults() {
        Logger[] log = new Logger[1];
        newLogger(log);

        PackDefaults defaults = PackDefaults.of("https://example.com/built-in-pack.zip", BUILT_IN_SHA1);
        TntLibraryConfig result = TntLibraryConfig.from(section("""
                resource-pack:
                  url: 'https://example.com/operator-pack.zip'
                  sha1: '%s'
                """.formatted(VALID_SHA1)), log[0], defaults);

        assertTrue(result.packDeliveryEnabled());
        assertEquals("https://example.com/operator-pack.zip", result.resourcePackUrl());
        assertEquals(VALID_SHA1, result.resourcePackSha1());
    }

    @Test
    void configSha1DifferingFromBuiltInLogsWarningButStillWins() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        PackDefaults defaults = PackDefaults.of("https://example.com/built-in-pack.zip", BUILT_IN_SHA1);
        TntLibraryConfig result = TntLibraryConfig.from(section("""
                resource-pack:
                  url: 'https://example.com/operator-pack.zip'
                  sha1: '%s'
                """.formatted(VALID_SHA1)), log[0], defaults);

        assertEquals(VALID_SHA1, result.resourcePackSha1(),
                "an explicit operator value must never be overridden");
        assertTrue(result.packDeliveryEnabled());
        assertEquals(1, handler.level(Level.WARNING).size());
        assertTrue(handler.level(Level.WARNING).get(0).getMessage().contains("stale"));
    }

    @Test
    void configSha1MatchingBuiltInLogsNoWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        PackDefaults defaults = PackDefaults.of("https://example.com/built-in-pack.zip", BUILT_IN_SHA1);
        TntLibraryConfig result = TntLibraryConfig.from(section("""
                resource-pack:
                  url: 'https://example.com/operator-pack.zip'
                  sha1: '%s'
                """.formatted(BUILT_IN_SHA1)), log[0], defaults);

        assertTrue(handler.level(Level.WARNING).isEmpty(),
                "a config sha1 matching the built-in one is not stale");
    }

    // ----------------------------------------------------------------------------------------
    // Partial-override refusal (the "worst combination available")
    // ----------------------------------------------------------------------------------------

    @Test
    void customUrlWithEmptySha1AndNonEmptyBuiltInDefaultIsRefusedAsPartialOverride() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        PackDefaults defaults = PackDefaults.of("https://example.com/built-in-pack.zip", BUILT_IN_SHA1);
        TntLibraryConfig result = TntLibraryConfig.from(section("""
                resource-pack:
                  url: 'https://example.com/custom-pack.zip'
                  sha1: ''
                """), log[0], defaults);

        assertFalse(result.packDeliveryEnabled(), "a partial override must disable pack delivery");
        assertEquals(1, handler.level(Level.WARNING).size());
        String message = handler.level(Level.WARNING).get(0).getMessage();
        assertTrue(message.contains("resource-pack.url"), "warning must name the overridden key");
        assertTrue(message.contains("resource-pack.sha1"), "warning must name the key that fell back");
        assertTrue(handler.level(Level.INFO).isEmpty(),
                "the partial-override warning replaces the unrelated 'not configured' INFO message");
    }

    @Test
    void customSha1WithEmptyUrlAndNonEmptyBuiltInDefaultIsRefusedAsPartialOverride() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        // sha1 matches the built-in default so the stale check does not also fire, isolating this
        // test to the partial-override warning alone.
        PackDefaults defaults = PackDefaults.of("https://example.com/built-in-pack.zip", VALID_SHA1);
        TntLibraryConfig result = TntLibraryConfig.from(section("""
                resource-pack:
                  url: ''
                  sha1: '%s'
                """.formatted(VALID_SHA1)), log[0], defaults);

        assertFalse(result.packDeliveryEnabled(), "a partial override must disable pack delivery");
        assertEquals(1, handler.level(Level.WARNING).size());
        String message = handler.level(Level.WARNING).get(0).getMessage();
        assertTrue(message.contains("resource-pack.sha1"), "warning must name the overridden key");
        assertTrue(message.contains("resource-pack.url"), "warning must name the key that fell back");
        assertTrue(handler.level(Level.INFO).isEmpty(),
                "the partial-override warning replaces the unrelated 'not configured' INFO message");
    }

    @Test
    void customUrlAndCustomSha1BothSetIsAllowedWithNoPartialOverrideWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        // sha1 matches the built-in default so the stale check does not also fire.
        PackDefaults defaults = PackDefaults.of("https://example.com/built-in-pack.zip", VALID_SHA1);
        TntLibraryConfig result = TntLibraryConfig.from(section("""
                resource-pack:
                  url: 'https://example.com/custom-pack.zip'
                  sha1: '%s'
                """.formatted(VALID_SHA1)), log[0], defaults);

        assertTrue(result.packDeliveryEnabled());
        assertEquals("https://example.com/custom-pack.zip", result.resourcePackUrl());
        assertEquals(VALID_SHA1, result.resourcePackSha1());
        assertTrue(handler.level(Level.WARNING).isEmpty(), "setting both keys is not a partial override");
    }

    @Test
    void bothEmptyWithNonEmptyBuiltInDefaultsEnablesDeliveryWithNoPartialOverrideWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        PackDefaults defaults = PackDefaults.of("https://example.com/built-in-pack.zip", BUILT_IN_SHA1);
        TntLibraryConfig result = TntLibraryConfig.from(section("""
                resource-pack:
                  url: ''
                  sha1: ''
                """), log[0], defaults);

        assertTrue(result.packDeliveryEnabled());
        assertEquals("https://example.com/built-in-pack.zip", result.resourcePackUrl());
        assertEquals(BUILT_IN_SHA1, result.resourcePackSha1());
        assertTrue(handler.level(Level.WARNING).isEmpty(), "neither key overridden is not a partial override");
    }

    @Test
    void bothEmptyWithEmptyBuiltInDefaultsDisablesDeliveryViaInfoNotWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);

        TntLibraryConfig result = TntLibraryConfig.from(section("""
                resource-pack:
                  url: ''
                  sha1: ''
                """), log[0], PackDefaults.empty());

        assertFalse(result.packDeliveryEnabled());
        assertTrue(handler.level(Level.WARNING).isEmpty(),
                "an empty built-in default must not be treated as a partial-override mismatch");
        assertEquals(1, handler.level(Level.INFO).size());
    }

    // ----------------------------------------------------------------------------------------
    // Canonical-constructor invariant
    // ----------------------------------------------------------------------------------------

    @Test
    void canonicalConstructorRecomputesPackDeliveryEnabledFromTheResolvedValues() {
        TntLibraryConfig lyingAboutABadUrl = new TntLibraryConfig(
                true, java.util.Map.of(), true, ProtectionProvider.AUTO,
                "not a url", VALID_SHA1, false, "prompt", true);
        assertFalse(lyingAboutABadUrl.packDeliveryEnabled(),
                "a malformed URL must force packDeliveryEnabled false regardless of what was passed");

        TntLibraryConfig lyingAboutHttp = new TntLibraryConfig(
                true, java.util.Map.of(), true, ProtectionProvider.AUTO,
                "http://example.com/pack.zip", VALID_SHA1, false, "prompt", true);
        assertFalse(lyingAboutHttp.packDeliveryEnabled(), "an http URL must force packDeliveryEnabled false");

        TntLibraryConfig lyingAboutSha1 = new TntLibraryConfig(
                true, java.util.Map.of(), true, ProtectionProvider.AUTO,
                "https://example.com/pack.zip", "NOTAHASH", false, "prompt", true);
        assertFalse(lyingAboutSha1.packDeliveryEnabled(), "an invalid sha1 must force packDeliveryEnabled false");

        TntLibraryConfig genuinelyValid = new TntLibraryConfig(
                true, java.util.Map.of(), true, ProtectionProvider.AUTO,
                "https://example.com/pack.zip", VALID_SHA1, false, "prompt", false);
        assertTrue(genuinelyValid.packDeliveryEnabled(),
                "valid inputs must enable delivery even if false was passed for the derived value");
    }

    @Test
    void anAbsentPromptFallsBackToADefaultPrompt() {
        Logger[] log = new Logger[1];
        newLogger(log);

        TntLibraryConfig result = TntLibraryConfig.from(section("""
                resource-pack:
                  url: 'https://example.com/pack.zip'
                  sha1: '%s'
                """.formatted(VALID_SHA1)), log[0], PackDefaults.empty());

        assertFalse(result.resourcePackPrompt().isEmpty(), "an absent prompt must fall back to a non-empty default");
    }
}
