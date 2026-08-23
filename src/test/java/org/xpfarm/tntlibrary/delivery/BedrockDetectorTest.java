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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
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
 * <p>This module's test classpath has no Floodgate dependency at all (by design -- see {@code
 * pom.xml}), which doubles as the "Floodgate absent" case: {@link BedrockDetector#attemptLink} must
 * resolve {@code null} here every time, exactly as it would on a real Java-only server.
 */
final class BedrockDetectorTest {

    private static final UUID SOME_PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void alwaysJavaNeverReportsBedrock() {
        assertFalse(BedrockDetector.alwaysJava().isBedrock(SOME_PLAYER));
    }

    @Test
    void attemptLinkReturnsNullWhenFloodgateClassIsAbsent() {
        assertNull(BedrockDetector.attemptLink(Logger.getLogger("test")));
    }

    @Test
    void attemptLinkWithNullLoggerDoesNotThrowWhenFloodgateIsAbsent() {
        assertDoesNotThrow(() -> BedrockDetector.attemptLink(null));
    }
}
