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

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * The plugin's own built-in resource-pack URL and SHA-1, baked into the JAR at build time from
 * {@code pack-defaults.properties} on the classpath.
 *
 * <p>That properties file is Maven-filtered: {@code pack.url} resolves {@code ${project.version}},
 * and {@code pack.sha1} resolves the {@code tnt.pack.sha1} POM property, which is empty by default
 * and overridden by CI with {@code -Dtnt.pack.sha1=<hash>} once PackSquash has produced the
 * published pack and its hash is known. A plain local build therefore ships an empty {@link
 * #sha1()}.
 *
 * <p>{@link TntLibraryConfig#from(org.bukkit.configuration.ConfigurationSection, Logger)} uses
 * these values only as a fallback: a non-empty {@code resource-pack.url}/{@code .sha1} in {@code
 * config.yml} always wins. See {@link TntLibraryConfig} for the full resolution order.
 *
 * <p>Loaded from the classpath via {@link #load(Logger)} at runtime, but this record can also be
 * built directly with {@link #of(String, String)} or {@link #empty()} -- the only two paths a
 * plain JUnit test needs, since neither depends on Maven's resource filtering having run.
 */
public record PackDefaults(String url, String sha1) {

    private static final String RESOURCE_PATH = "/pack-defaults.properties";

    public PackDefaults {
        url = sanitize(url);
        sha1 = sanitize(sha1);
    }

    /**
     * Normalises one built-in value. {@code null} becomes empty, and so does a value that still
     * contains an unresolved Maven placeholder ({@code ${...}}).
     *
     * <p>The placeholder case is the load-bearing one. {@code pack-defaults.properties} is
     * Maven-filtered, and {@code pack.sha1} interpolates the {@code tnt.pack.sha1} POM property,
     * which is empty by default. Most Maven versions filter an empty property to an empty string,
     * but some environments leave the literal {@code ${tnt.pack.sha1}} in the file instead -- so a
     * plain build with no {@code -Dtnt.pack.sha1} can ship either an empty value or the raw
     * placeholder. Both mean exactly the same thing -- "no built-in pack for this build" -- so both
     * must collapse to empty here. Without this, the literal placeholder would flow into {@link
     * TntLibraryConfig} as an invalid SHA-1 and provoke a spurious WARNING on a build that simply
     * has no baked-in pack yet.
     */
    private static String sanitize(String value) {
        if (value == null || value.contains("${")) {
            return "";
        }
        return value;
    }

    /** Builds a {@link PackDefaults} directly from known values, bypassing the classpath resource
     *  entirely -- what tests use so they never depend on the build's Maven filtering. */
    public static PackDefaults of(String url, String sha1) {
        return new PackDefaults(url, sha1);
    }

    /** Equivalent to a plugin JAR built without {@code -Dtnt.pack.sha1}: no built-in pack. */
    public static PackDefaults empty() {
        return new PackDefaults("", "");
    }

    /**
     * Reads {@code pack-defaults.properties} from the classpath. A missing or unreadable resource
     * is treated the same as an empty build-time default -- logged at WARNING, since it would mean
     * the JAR itself was built incorrectly, but never thrown: the plugin must still enable with the
     * built-in pack simply unavailable, exactly as if the property had never been set.
     */
    public static PackDefaults load(Logger logger) {
        Objects.requireNonNull(logger, "logger must not be null");
        try (InputStream in = PackDefaults.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                logger.warning("pack-defaults.properties is missing from the plugin JAR; "
                        + "the built-in resource pack is unavailable.");
                return empty();
            }
            Properties props = new Properties();
            props.load(in);
            return new PackDefaults(props.getProperty("pack.url", ""), props.getProperty("pack.sha1", ""));
        } catch (IOException e) {
            logger.warning("Failed to read pack-defaults.properties from the plugin JAR ("
                    + e.getMessage() + "); the built-in resource pack is unavailable.");
            return empty();
        }
    }
}
