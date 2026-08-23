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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Pattern;
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
 *
 * <h2>Resolution order for {@code resource-pack.url} / {@code resource-pack.sha1}</h2>
 *
 * <p>A non-empty value in {@code config.yml} always wins -- an explicit operator override. An empty
 * value falls back to {@link PackDefaults}, the URL/SHA-1 the plugin's own release baked into itself
 * at build time. Only if the winning value is <em>still</em> empty -- a local dev build with no
 * {@code -Dtnt.pack.sha1} -- does this mean "not configured". This is what lets a plugin upgrade ship
 * its own matching pack with zero operator action, while still letting an operator who has
 * deliberately pinned a different pack keep it.
 *
 * <p>{@code url} and {@code sha1} are resolved independently, which creates one dangerous
 * combination: an operator who sets only one of the two keys in {@code config.yml} gets that key's
 * custom value paired with the built-in default for the other -- two individually well-formed values
 * that almost certainly belong to two different packs. That partial override is caught as a
 * dedicated rule (see {@link #from(ConfigurationSection, Logger, PackDefaults)}) that disables pack
 * delivery and warns, rather than letting the client discover the mismatch itself via a failed hash
 * check. A {@code config.yml} sha1 that differs from this version's built-in hash still wins but is
 * warned about as possibly stale.
 */
public record TntLibraryConfig(
        boolean masterEnabled,
        Map<String, BombSettings> bombs,
        boolean respectRegions,
        ProtectionProvider provider,
        String resourcePackUrl,
        String resourcePackSha1,
        boolean resourcePackRequired,
        String resourcePackPrompt,
        boolean packDeliveryEnabled) {

    /** Exactly 40 lowercase hex characters -- the only shape accepted without a warning. */
    private static final Pattern SHA1_LOWERCASE = Pattern.compile("^[a-f0-9]{40}$");

    /** Same length and charset as {@link #SHA1_LOWERCASE} but case-insensitive, to detect the
     *  specific "right hash, wrong case" mistake and explain it rather than just saying "invalid". */
    private static final Pattern SHA1_ANY_CASE = Pattern.compile("^[a-fA-F0-9]{40}$");

    /** Player-facing prompt shown alongside the pack when {@code resource-pack.prompt} is unset. */
    private static final String DEFAULT_PROMPT = "TNT Library adds custom explosive textures.";

    /**
     * Re-derives the bomb map so it can never contradict {@link #masterEnabled()}, re-derives {@link
     * #packDeliveryEnabled()} so it can never contradict the resolved URL/SHA-1, and makes the map
     * defensively immutable.
     *
     * <p>Whatever a caller passes, when {@code masterEnabled} is {@code false} every bomb is rewritten
     * to its {@link BombSettings#asDisabled()} form; when it is {@code true} the bombs are stored as
     * given. The resulting map is wrapped unmodifiable and its {@code provider} normalised away from
     * {@code null}, so a hand-built instance claiming a bomb is enabled under a disabled plugin is
     * unrepresentable.
     *
     * <p>{@code packDeliveryEnabled} is likewise recomputed from the values that actually govern it:
     * whatever a caller passes, it becomes {@code true} only when the resolved url is a syntactically
     * valid {@code https} URI and the resolved sha1 is 40 lowercase hex characters. A hand-built
     * instance claiming delivery is enabled alongside a malformed URL is therefore unrepresentable --
     * mirroring the {@code packDeliveryEnabled} recompute discipline in the sibling RedstoneStuff
     * plugin. This stays silent by design -- {@link #from(ConfigurationSection, Logger)} owns every
     * operator-facing INFO and WARNING, and this class must never throw for a bad value.
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
        resourcePackPrompt = resourcePackPrompt == null || resourcePackPrompt.isEmpty()
                ? DEFAULT_PROMPT
                : resourcePackPrompt;
        packDeliveryEnabled = isUrlAcceptable(resourcePackUrl) && isSha1Acceptable(resourcePackSha1);
    }

    /**
     * Convenience overload that loads {@link PackDefaults} from the classpath. This is what the
     * plugin's own main class calls; use {@link #from(ConfigurationSection, Logger, PackDefaults)}
     * in tests that need to control the built-in fallback without depending on Maven's resource
     * filtering having run.
     */
    public static TntLibraryConfig from(ConfigurationSection config, Logger logger) {
        Objects.requireNonNull(logger, "logger");
        return from(config, logger, PackDefaults.load(logger));
    }

    /**
     * Reads and validates every key documented in the shipped {@code config.yml}, resolves {@code
     * resource-pack.url}/{@code .sha1} against {@code defaults} (see the class javadoc for the
     * resolution order), logs the result, and returns an immutable snapshot. Missing keys and
     * missing sections both fall back to the shipped defaults silently; present-but-invalid values
     * fall back with a WARNING naming the key.
     *
     * @param config the root section -- typically a plugin's {@code FileConfiguration}, or a {@code
     *     YamlConfiguration} loaded directly from a string in tests; must not be null
     * @param logger where INFO (unconfigured) and WARNING (invalid or stale) messages are written;
     *     must not be null
     * @param defaults the plugin's built-in fallback URL/SHA-1, used only where {@code config}
     *     leaves the corresponding value empty; must not be null
     */
    public static TntLibraryConfig from(
            ConfigurationSection config, Logger logger, PackDefaults defaults) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(defaults, "defaults");

        boolean masterEnabled = booleanOrWarn(config, "enabled", true, logger);

        Map<String, BombSettings> bombs = new LinkedHashMap<>();
        for (BombType type : BombType.values()) {
            bombs.put(type.id(), readBomb(config, type, logger));
        }

        boolean respectRegions = booleanOrWarn(config, "protection.respect-regions", true, logger);
        ProtectionProvider provider = ProtectionProvider.from(config, "protection.provider", logger);

        String rawUrl = config.getString("resource-pack.url", "");
        String rawSha1 = config.getString("resource-pack.sha1", "");
        String configUrl = rawUrl == null ? "" : rawUrl.trim();
        String configSha1 = rawSha1 == null ? "" : rawSha1.trim();
        boolean required = booleanOrWarn(config, "resource-pack.required", false, logger);
        String prompt = config.getString("resource-pack.prompt", DEFAULT_PROMPT);

        boolean urlOverridden = !configUrl.isEmpty();
        boolean sha1Overridden = !configSha1.isEmpty();

        warnIfOverrideLooksStale(sha1Overridden, configSha1, defaults.sha1(), logger);

        boolean partialOverride =
                warnIfPartialOverride(urlOverridden, sha1Overridden, defaults, logger);

        // A partial override is refused outright: clearing the non-overridden side to "" (rather
        // than letting it fall back to a built-in default that almost certainly belongs to a
        // different pack) forces both the check below and the canonical constructor's own invariant
        // recomputation to agree that delivery must stay disabled -- an empty value can never pass
        // isUrlAcceptable/isSha1Acceptable, so there is no way for the constructor to silently
        // re-enable what this method just refused.
        String resourcePackUrl;
        String resourcePackSha1;
        if (partialOverride) {
            resourcePackUrl = urlOverridden ? configUrl : "";
            resourcePackSha1 = sha1Overridden ? configSha1 : "";
        } else {
            resourcePackUrl = urlOverridden ? configUrl : defaults.url();
            resourcePackSha1 = sha1Overridden ? configSha1 : defaults.sha1();
        }

        boolean urlEmpty = resourcePackUrl == null || resourcePackUrl.isEmpty();
        boolean sha1Empty = resourcePackSha1 == null || resourcePackSha1.isEmpty();

        // Validate for the operator-facing WARNINGs. The canonical constructor recomputes
        // packDeliveryEnabled from the same rules, so these calls are purely for logging.
        if (!urlEmpty) {
            validateUrl(resourcePackUrl, logger);
        }
        if (!sha1Empty) {
            validateSha1(resourcePackSha1, logger);
        }

        // The partial-override warning above already explains why delivery is disabled; do not
        // also log the unrelated "not configured" INFO message for the side we just cleared.
        if (!partialOverride) {
            logUnconfigured(urlEmpty, sha1Empty, logger);
        }

        return new TntLibraryConfig(
                masterEnabled,
                bombs,
                respectRegions,
                provider,
                resourcePackUrl == null ? "" : resourcePackUrl,
                resourcePackSha1 == null ? "" : resourcePackSha1,
                required,
                prompt,
                false);
    }

    /** Scheme-and-syntax acceptability of a resolved url, without the logging. See {@link #validateUrl}. */
    private static boolean isUrlAcceptable(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) {
            return false;
        }
        try {
            String scheme = new URI(rawUrl).getScheme();
            return scheme != null && scheme.equalsIgnoreCase("https");
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** Shape acceptability of a resolved sha1, without the logging. See {@link #validateSha1}. */
    private static boolean isSha1Acceptable(String rawSha1) {
        return rawSha1 != null && SHA1_LOWERCASE.matcher(rawSha1).matches();
    }

    /**
     * An operator override is never replaced -- it always wins over the built-in default, even when
     * it looks stale. But a {@code config.yml} {@code sha1} that differs from this plugin version's
     * built-in hash is exactly the shape of a server that was upgraded from an earlier release and
     * never cleared its old pin, so it gets a WARNING naming the actionable fix rather than silent
     * use. Only compares when a built-in default actually exists (a local dev build's empty default
     * is not "stale", it is simply absent).
     */
    private static void warnIfOverrideLooksStale(
            boolean sha1Overridden, String configSha1, String defaultSha1, Logger logger) {
        if (sha1Overridden
                && defaultSha1 != null
                && !defaultSha1.isEmpty()
                && !configSha1.equals(defaultSha1)) {
            logger.warning("resource-pack.sha1 in config.yml ('" + configSha1 + "') differs from "
                    + "this plugin version's built-in pack hash ('" + defaultSha1 + "'); the "
                    + "configured value may be stale for this version. It still wins -- an explicit "
                    + "operator setting is never overridden -- but if it was left over from an "
                    + "earlier release, clearing both resource-pack.url and resource-pack.sha1 in "
                    + "config.yml adopts this version's built-in pack automatically.");
        }
    }

    /**
     * The partial-override rule: {@code url} and {@code sha1} each fall back to the built-in default
     * only when their own {@code config.yml} value is empty. If exactly one of the two is overridden
     * in {@code config.yml} and the other one's built-in default is non-empty, the resolved pair
     * combines a custom value with a built-in value from what is almost certainly a <em>different</em>
     * pack. Both halves are individually well-formed, so ordinary validation would pass and delivery
     * would enable -- until the client downloads the pack, finds the hash does not match, and throws,
     * disconnecting the player when {@code resource-pack.required} is {@code true}. Caught here
     * instead: log a WARNING naming both the overridden key and the one that fell back, and refuse
     * the combination (the caller clears the non-overridden side to {@code ""}, which disables
     * delivery). When the built-in default for the missing side is itself empty (a local dev build
     * with no {@code -Dtnt.pack.sha1}), this is not a partial override -- it is the ordinary "not
     * configured" path handled by {@link #logUnconfigured}, which keeps logging at INFO.
     *
     * @return {@code true} if this was a partial override that must be refused
     */
    private static boolean warnIfPartialOverride(
            boolean urlOverridden, boolean sha1Overridden, PackDefaults defaults, Logger logger) {
        if (urlOverridden == sha1Overridden) {
            return false; // both or neither overridden -- not a partial override
        }
        String fallbackKey = urlOverridden ? "resource-pack.sha1" : "resource-pack.url";
        String fallbackValue = urlOverridden ? defaults.sha1() : defaults.url();
        if (fallbackValue == null || fallbackValue.isEmpty()) {
            return false; // no built-in default to mismatch against -- ordinary "not configured"
        }
        String overriddenKey = urlOverridden ? "resource-pack.url" : "resource-pack.sha1";
        logger.warning("config.yml sets " + overriddenKey + " but leaves " + fallbackKey + " empty; "
                + "the empty key falls back to this plugin version's built-in default, which almost "
                + "certainly belongs to a different resource pack than the one named by "
                + overriddenKey + ". Both values are individually well-formed, so they would pass "
                + "validation, but the client's hash check would fail and the download would throw "
                + "-- disconnecting the player when resource-pack.required is true. Set both "
                + "resource-pack.url and resource-pack.sha1 in config.yml to values from the same "
                + "pack, or leave both empty to use this version's built-in pack; pack delivery "
                + "disabled.");
        return true;
    }

    /**
     * An empty <em>resolved</em> {@code url} or {@code sha1} -- meaning both {@code config.yml} and
     * the built-in {@link PackDefaults} left it empty, e.g. a local dev build with no {@code
     * -Dtnt.pack.sha1} -- is the documented "not configured at all" state, not an error. Log at
     * INFO, never WARNING, so an operator who has not overridden anything, on a build that also has
     * no built-in pack, does not see a scary message on every boot.
     */
    private static void logUnconfigured(boolean urlEmpty, boolean sha1Empty, Logger logger) {
        if (urlEmpty && sha1Empty) {
            logger.info("resource-pack.url and resource-pack.sha1 are not configured yet; "
                    + "pack delivery is disabled until both are set.");
        } else if (urlEmpty) {
            logger.info("resource-pack.url is not configured yet; pack delivery is disabled until it is set.");
        } else if (sha1Empty) {
            logger.info("resource-pack.sha1 is not configured yet; pack delivery is disabled until it is set.");
        }
    }

    /**
     * {@code url} must parse as a {@link URI} with scheme {@code https}. {@code http} is refused
     * with a dedicated message rather than folded into the generic "wrong scheme" case, because it
     * is the one wrong value an operator is likely to type on purpose.
     *
     * @return {@code true} if the url is an acceptable https URI
     */
    private static boolean validateUrl(String rawUrl, Logger logger) {
        URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (URISyntaxException e) {
            logger.warning("resource-pack.url '" + rawUrl + "' is not a valid URI (" + e.getMessage()
                    + "); pack delivery disabled.");
            return false;
        }

        String scheme = uri.getScheme();
        if (scheme != null && scheme.equalsIgnoreCase("http")) {
            logger.warning("resource-pack.url '" + rawUrl + "' uses http, which is refused: the JDK "
                    + "will not follow a redirect that changes scheme (JDK-4620571), and virtually "
                    + "every host upgrades http to https, so an http pack URL would fail at the "
                    + "client with no server-side signal. Use an https URL instead; pack delivery "
                    + "disabled.");
            return false;
        }
        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            logger.warning("resource-pack.url '" + rawUrl + "' must use the https scheme (found '"
                    + (scheme == null ? "none" : scheme) + "'); pack delivery disabled.");
            return false;
        }
        return true;
    }

    /**
     * {@code sha1} must match {@code ^[a-f0-9]{40}$} exactly. Uppercase hex of the correct length is
     * refused rather than silently lowercased and gets its own message: the server-side validity
     * gate is strict, and a hash the client cannot parse silently degrades to "no hash", which means
     * the pack re-downloads on every join with no integrity check.
     *
     * @return {@code true} if the sha1 is exactly 40 lowercase hex characters
     */
    private static boolean validateSha1(String rawSha1, Logger logger) {
        if (SHA1_LOWERCASE.matcher(rawSha1).matches()) {
            return true;
        }
        if (SHA1_ANY_CASE.matcher(rawSha1).matches()) {
            logger.warning("resource-pack.sha1 '" + rawSha1 + "' contains uppercase hex characters, "
                    + "which is refused rather than silently lowercased: the server-side validity "
                    + "gate is strict, and a hash the client cannot parse silently degrades to \"no "
                    + "hash\", which means the pack re-downloads on every join with no integrity "
                    + "check. Use 40 lowercase hex characters; pack delivery disabled.");
        } else {
            logger.warning("resource-pack.sha1 '" + rawSha1 + "' must be exactly 40 lowercase hex "
                    + "characters; pack delivery disabled.");
        }
        return false;
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
     * Whether the resolved resource pack is configured at all: both {@code url} and {@code sha1}
     * non-empty. An empty url or sha1 means "not configured" -- delivery is simply off, which is not
     * an error. This is a weaker predicate than {@link #packDeliveryEnabled()}: a pack can be
     * configured (both values present) yet still fail delivery if one of them is malformed. Delivery
     * itself is gated on {@link #packDeliveryEnabled()}.
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
