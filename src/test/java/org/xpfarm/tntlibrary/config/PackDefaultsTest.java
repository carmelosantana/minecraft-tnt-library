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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PackDefaults}, exercising {@link PackDefaults#load(Logger)} against the real
 * {@code pack-defaults.properties} on the test classpath -- i.e. the same Maven-resource-filtered
 * file the shipped JAR carries, produced by {@code process-resources} before this test runs. A plain
 * {@code mvn test}/{@code verify} with no {@code -Dtnt.pack.sha1} leaves {@code pack.sha1} empty,
 * exactly like the shipped default.
 */
final class PackDefaultsTest {

    /**
     * Anchors on the exact GitHub release-asset layout, with a backreference so the same version has
     * to appear in both the tag and the filename -- without hardcoding a version number that would go
     * stale on every release.
     */
    private static final Pattern URL_SHAPE = Pattern.compile(
            "^https://github\\.com/carmelosantana/minecraft-tnt-library/releases/download/"
                    + "v([^/]+)/tnt-library-pack-\\1\\.zip$");

    @Test
    void loadReadsTheClasspathResourceWithNoUnresolvedPlaceholders() {
        PackDefaults defaults = PackDefaults.load(Logger.getLogger(getClass().getName()));

        assertFalse(defaults.url().contains("${"),
                "pack.url must have no unresolved Maven placeholder, found: " + defaults.url());
        assertFalse(defaults.sha1().contains("${"),
                "pack.sha1 must have no unresolved Maven placeholder, found: " + defaults.sha1());
    }

    @Test
    void loadedUrlMatchesTheReleaseAssetShapeAndIncludesAVersion() {
        PackDefaults defaults = PackDefaults.load(Logger.getLogger(getClass().getName()));

        assertTrue(URL_SHAPE.matcher(defaults.url()).matches(),
                "pack.url does not match the expected release-asset shape: " + defaults.url());
    }

    @Test
    void ofNeverReturnsNullFields() {
        PackDefaults defaults = PackDefaults.of(null, null);

        assertTrue(defaults.url().isEmpty());
        assertTrue(defaults.sha1().isEmpty());
    }

    @Test
    void emptyHasBothFieldsEmpty() {
        PackDefaults defaults = PackDefaults.empty();

        assertTrue(defaults.url().isEmpty());
        assertTrue(defaults.sha1().isEmpty());
    }
}
