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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Validated, immutable view over {@code config.yml}.
 *
 * <p>Build one with {@link #from(ConfigurationSection, Logger)}. The factory takes the narrowest
 * Bukkit type that carries the data it needs -- a {@link ConfigurationSection}, which {@code
 * YamlConfiguration} implements -- specifically so it can be constructed in a plain JUnit test from
 * a YAML string with no server running. Do not widen the parameter to {@code JavaPlugin} or {@code
 * FileConfiguration}; every value this class needs is reachable through {@link ConfigurationSection},
 * and widening it would break that headless testability for no gain.
 *
 * <h2>Validation philosophy</h2>
 *
 * <p>This class never throws for a bad config value and never prevents the plugin from enabling. A
 * missing key falls back to the documented default silently (an absent key is not a mistake). A key
 * that is <em>present but invalid</em> -- wrong type, or a number outside its allowed range -- falls
 * back to the same default and logs a WARNING naming the offending key. One bad value never discards
 * its valid neighbours: every bomb, and every field within a bomb, is read independently.
 *
 * <p>A null {@code config} or {@code logger} is a different thing -- a wiring mistake in the plugin's
 * own code, not an operator's typo -- and fails fast with {@link NullPointerException}.
 *
 * <h2>The master switch</h2>
 *
 * <p>{@code enabled: false} at the file root disables the whole plugin. Rather than force every call
 * site to remember to check {@link #masterEnabled()} before acting on a bomb, the master switch is
 * folded into each {@link BombSettings} at construction time: when {@link #masterEnabled()} is
 * {@code false}, the canonical constructor rewrites every entry of the bomb map to its disabled form
 * (see {@link #TntLibraryConfig}). A bomb that reports {@link BombSettings#enabled()} {@code true}
 * while the plugin's master switch is off is therefore not merely discouraged, it is
 * unrepresentable -- mirroring the {@code packDeliveryEnabled} recompute discipline in the sibling
 * RedstoneStuff plugin.
 */
public record TntLibraryConfig(
        boolean masterEnabled,
        Map<String, BombSettings> bombs,
        boolean respectRegions,
        ProtectionProvider provider,
        String resourcePackUrl,
        String resourcePackSha1,
        boolean resourcePackRequired) {

    /**
     * Re-derives the bomb map so it can never contradict {@link #masterEnabled()}, and makes the map
     * defensively immutable.
     *
     * <p>Whatever a caller passes, when {@code masterEnabled} is {@code false} every bomb is rewritten
     * to its {@link BombSettings#asDisabled()} form; when it is {@code true} the bombs are stored as
     * given. The resulting map is wrapped unmodifiable and its {@code provider} normalised away from
     * {@code null}, so a hand-built instance claiming a bomb is enabled under a disabled plugin is
     * unrepresentable. This stays silent by design -- {@link #from(ConfigurationSection, Logger)} owns
     * every operator-facing WARNING, and this class must never throw for a bad value.
     */
    public TntLibraryConfig {
        Objects.requireNonNull(bombs, "bombs");
        Map<String, BombSettings> gated = new LinkedHashMap<>(bombs.size() * 2);
        for (Map.Entry<String, BombSettings> entry : bombs.entrySet()) {
            BombSettings value = entry.getValue();
            gated.put(entry.getKey(), masterEnabled ? value : value.asDisabled());
        }
        bombs = Collections.unmodifiableMap(gated);
        provider = provider == null ? ProtectionProvider.AUTO : provider;
        resourcePackUrl = resourcePackUrl == null ? "" : resourcePackUrl;
        resourcePackSha1 = resourcePackSha1 == null ? "" : resourcePackSha1;
    }

    /**
     * Reads and validates every key documented in the shipped {@code config.yml} and returns an
     * immutable snapshot. Missing keys and missing sections both fall back to the shipped defaults
     * silently; present-but-invalid values fall back with a WARNING naming the key.
     *
     * @param config the root section -- typically a plugin's {@code FileConfiguration}, or a {@code
     *     YamlConfiguration} loaded directly from a string in tests; must not be null
     * @param logger where WARNINGs about invalid values are written; must not be null
     */
    public static TntLibraryConfig from(ConfigurationSection config, Logger logger) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(logger, "logger");

        boolean masterEnabled = booleanOrWarn(config, "enabled", true, logger);

        Map<String, BombSettings> bombs = new LinkedHashMap<>();
        for (BombType type : BombType.values()) {
            bombs.put(type.id(), readBomb(config, type, logger));
        }

        boolean respectRegions = booleanOrWarn(config, "protection.respect-regions", true, logger);
        ProtectionProvider provider = ProtectionProvider.from(config, "protection.provider", logger);

        String url = config.getString("resource-pack.url", "");
        String sha1 = config.getString("resource-pack.sha1", "");
        boolean required = booleanOrWarn(config, "resource-pack.required", false, logger);

        return new TntLibraryConfig(
                masterEnabled,
                bombs,
                respectRegions,
                provider,
                url == null ? "" : url.trim(),
                sha1 == null ? "" : sha1.trim(),
                required);
    }

    /** Reads one bomb's subtree, mapping its own key names onto {@link BombSettings}'s fields. */
    private static BombSettings readBomb(ConfigurationSection config, BombType type, Logger logger) {
        String base = "bombs." + type.id() + ".";
        boolean enabled = booleanOrWarn(config, base + "enabled", type.defaultEnabled(), logger);
        int radius = type.radiusKey() == null
                ? 0
                : wholeNumberAtLeast(config, base + type.radiusKey(), 1, type.radiusDefault(), logger);
        int fuseTicks = wholeNumberAtLeast(config, base + type.fuseKey(), 1, type.fuseDefault(), logger);
        int hangTicks = type.hangKey() == null
                ? 0
                : wholeNumberAtLeast(config, base + type.hangKey(), 1, type.hangDefault(), logger);
        return new BombSettings(enabled, radius, fuseTicks, hangTicks);
    }

    /**
     * Looks up a bomb's settings by id, never returning {@code null}. An unknown or unconfigured id
     * yields {@link BombSettings#DISABLED} -- a sensible disabled default -- rather than crashing the
     * caller. Because the map was already gated by the master switch in the canonical constructor,
     * the returned settings already reflect {@link #masterEnabled()}.
     */
    public BombSettings bomb(String id) {
        BombSettings settings = bombs.get(id);
        return settings != null ? settings : BombSettings.DISABLED;
    }

    /**
     * Whether the resource pack is configured at all: both {@code url} and {@code sha1} non-empty.
     * An empty url or sha1 means "not configured" -- delivery is simply off, which is not an error.
     * (Full URL/SHA-1 well-formedness validation lands with the delivery layer at the dev gate; this
     * layer only distinguishes configured from not-configured.)
     */
    public boolean resourcePackConfigured() {
        return !resourcePackUrl.isEmpty() && !resourcePackSha1.isEmpty();
    }

    /**
     * Reads a boolean key, warning on a present-but-wrong-typed value.
     *
     * <p>Unlike {@link ConfigurationSection#getBoolean(String, boolean)} -- which cannot tell a
     * wrong-typed value from an absent one and would silently return the default for {@code
     * respect-regions: "yes"} (a quoted string, not a YAML boolean) -- this reads the raw object so a
     * present non-boolean is reported. An absent key stays silent: it is not a misconfiguration.
     */
    private static boolean booleanOrWarn(
            ConfigurationSection root, String key, boolean fallback, Logger logger) {
        Object raw = root.get(key, null);
        if (raw == null || raw instanceof ConfigurationSection) {
            // absent, nested under a scalar, or a mapping written where a scalar was expected: the
            // default, silently. A ConfigurationSection is a structural mistake, not a wrong scalar,
            // and its toString is Bukkit-internal noise that must never reach an operator's WARNING.
            return fallback;
        }
        if (raw instanceof Boolean bool) {
            return bool;
        }
        warn(key, raw, "true or false", fallback, logger);
        return fallback;
    }

    /**
     * Reads an int key with an inclusive lower bound, warning on a present-but-invalid value and
     * reporting the value the operator actually wrote.
     *
     * <p>This deliberately reads the raw object rather than calling {@link
     * ConfigurationSection#getInt(String, int)}: that method funnels every {@code Number} through
     * {@code Number#intValue()}, which truncates a fraction and <em>wraps</em> an out-of-range
     * magnitude, so a range check downstream would only ever see a number the operator never typed
     * (e.g. {@code 4294967300} wrapping to {@code 4}). Reading {@code doubleValue()} instead is exact
     * for every int-range integer and keeps an out-of-range or unrepresentable magnitude out of range
     * rather than coercing it into a plausible-looking small int. The WARNING quotes the raw value.
     */
    private static int wholeNumberAtLeast(
            ConfigurationSection root, String key, int minimum, int fallback, Logger logger) {
        Object raw = root.get(key, null);
        if (raw == null || raw instanceof ConfigurationSection) {
            // absent, nested under a scalar, or a mapping written where a scalar was expected: the
            // default, silently -- a structural mistake, not a wrong scalar, and its toString is
            // Bukkit-internal noise that must never reach an operator's WARNING.
            return fallback;
        }
        if (!(raw instanceof Number number)) {
            warn(key, raw, "a whole number of " + minimum + " or more", fallback, logger);
            return fallback;
        }
        double written = number.doubleValue();
        if (!Double.isFinite(written)
                || written != Math.floor(written)
                || written < minimum
                || written > Integer.MAX_VALUE) {
            warn(key, raw, "a whole number of " + minimum + " or more", fallback, logger);
            return fallback;
        }
        return (int) written;
    }

    private static void warn(
            String key, Object value, String requirement, Object fallback, Logger logger) {
        logger.warning(key + " is " + value + " but must be " + requirement
                + "; falling back to " + fallback + ".");
    }
}
