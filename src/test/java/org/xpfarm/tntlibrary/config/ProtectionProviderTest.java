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

/** Focused unit tests for {@link ProtectionProvider#from}. */
final class ProtectionProviderTest {

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
                ProtectionProviderTest.class.getName() + "." + LOGGER_SEQ.incrementAndGet());
        logger.setUseParentHandlers(false);
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);
        out[0] = logger;
        return handler;
    }

    private static ConfigurationSection section(String yaml) {
        return YamlConfiguration.loadConfiguration(new StringReader(yaml));
    }

    @Test
    void everyConstantParsesFromItsLowercaseToken() {
        for (ProtectionProvider provider : ProtectionProvider.values()) {
            Logger[] log = new Logger[1];
            RecordingHandler handler = newLogger(log);
            ConfigurationSection config =
                    section("p: " + provider.name().toLowerCase(java.util.Locale.ROOT) + "\n");
            assertEquals(provider, ProtectionProvider.from(config, "p", log[0]));
            assertTrue(handler.level(Level.WARNING).isEmpty());
        }
    }

    @Test
    void surroundingWhitespaceIsTolerated() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);
        assertEquals(ProtectionProvider.WORLDGUARD,
                ProtectionProvider.from(section("p: '  worldguard  '\n"), "p", log[0]));
        assertTrue(handler.level(Level.WARNING).isEmpty());
    }

    @Test
    void anAbsentKeyIsAutoWithoutWarning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);
        assertEquals(ProtectionProvider.AUTO, ProtectionProvider.from(section("{}"), "p", log[0]));
        assertTrue(handler.level(Level.WARNING).isEmpty());
    }

    @Test
    void anUnknownTokenIsAutoWithWarningNamingKeyAndValue() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = newLogger(log);
        assertEquals(ProtectionProvider.AUTO, ProtectionProvider.from(section("p: banana\n"), "p", log[0]));
        assertEquals(1, handler.level(Level.WARNING).size());
        String message = handler.level(Level.WARNING).get(0).getMessage();
        assertTrue(message.contains("p"), message);
        assertTrue(message.contains("banana"), message);
    }
}
