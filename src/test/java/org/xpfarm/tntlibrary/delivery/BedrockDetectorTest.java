/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.delivery;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the server-independent half of {@link BedrockDetector}.
 *
 * <p>{@link BedrockDetector#create(Logger)} itself defers to {@code Bukkit.getPluginManager()},
 * which needs a running server, so it is not exercised here -- only {@code attemptLink} (deliberately
 * package-private and split out for exactly this reason) and the no-server {@link
 * BedrockDetector#alwaysJava()} path are tested directly.
 *
 * <p><strong>Classpath note (Phase 2).</strong> Since the Smart Bomb, this module compiles against
 * the Floodgate/Cumulus {@code api} at <em>provided</em> scope (for the Bedrock {@code CustomForm}),
 * and Maven puts provided dependencies on the test classpath too. So {@code
 * org.geysermc.floodgate.api.FloodgateApi} now resolves here -- the earlier "class simply absent"
 * simulation of a Java-only server is no longer what happens. The behaviour under test is instead the
 * <em>next</em> guard down: the class links, but no Floodgate <em>instance</em> is running (there is
 * no server), so {@code FloodgateApi.getInstance()} returns {@code null} and {@code attemptLink} must
 * still resolve to {@code null} -- the exact safe Java-only fallback, reached one branch later. That
 * guard is the one {@link BedrockDetector#attemptLink} documents as "a Floodgate that enables but
 * returns a null instance must be treated exactly like Floodgate being absent".
 */
final class BedrockDetectorTest {

    private static final UUID SOME_PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    /** Captures emitted {@link LogRecord}s so a test can keep console output clean and assert on it. */
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
    }

    /** A fresh logger that does not propagate to the root handlers, with a recording handler attached. */
    private static RecordingHandler quietLogger(Logger[] out) {
        Logger logger = Logger.getLogger(BedrockDetectorTest.class.getName() + "." + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);
        out[0] = logger;
        return handler;
    }

    @Test
    void alwaysJavaNeverReportsBedrock() {
        assertFalse(BedrockDetector.alwaysJava().isBedrock(SOME_PLAYER));
    }

    @Test
    void attemptLinkReturnsNullWhenNoFloodgateInstanceIsRunning() {
        Logger[] log = new Logger[1];
        RecordingHandler handler = quietLogger(log);

        // The api class links (provided dep on the test classpath), but no Floodgate server is
        // running, so getInstance() is null -- attemptLink must fall back to null, not link a
        // detector that would NPE on every isBedrock call.
        assertNull(BedrockDetector.attemptLink(log[0]));

        // The fallback is announced exactly once, at WARNING, so an operator can see why Bedrock
        // detection is inert; test output stays pristine because the logger does not propagate.
        long warnings = handler.records.stream().filter(r -> r.getLevel().equals(Level.WARNING)).count();
        assertEquals(1, warnings, "the null-instance fallback must log exactly one WARNING");
    }

    @Test
    void attemptLinkWithNullLoggerDoesNotThrow() {
        // A null logger must never NPE the fallback path, even when it has something to warn about.
        assertDoesNotThrow(() -> assertNull(BedrockDetector.attemptLink(null)));
    }
}
