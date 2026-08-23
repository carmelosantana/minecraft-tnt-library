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

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;

/**
 * Tells whether a player is connected through Floodgate (a Bedrock client behind Geyser), so
 * {@link ResourcePackDeliveryListener} can skip sending them a Java resource pack -- a Java pack
 * URL is meaningless to a Bedrock client, which already gets its own pack from Geyser.
 *
 * <p>Floodgate is a <em>soft</em> dependency: this plugin's {@code pom.xml} deliberately does not
 * depend on it, so the Floodgate API class must never be linked when the plugin is absent from the
 * server. This class checks {@code Bukkit.getPluginManager().isPluginEnabled("floodgate")} first
 * and only then reaches for Floodgate's API via {@link Class#forName}. Any failure anywhere in that
 * chain -- the plugin absent, the class or method missing, a version mismatch, a reflective
 * invocation failure -- resolves every player to "not Bedrock" and logs nothing scary.
 *
 * <h2>Why the Floodgate link is resolved lazily, on first {@link #isBedrock(UUID)} call</h2>
 *
 * <p>{@code plugin.yml} deliberately declares no dependency on Floodgate, so nothing guarantees
 * Floodgate has already enabled by the time this plugin does. Resolving the link eagerly at
 * construction time -- i.e. when {@code TntLibraryPlugin} builds its delivery listener in {@code
 * onEnable} -- would check {@code isPluginEnabled("floodgate")} before Floodgate had a chance to
 * enable, if this plugin happens to enable first, and would then latch "no Floodgate" into the
 * instance for the rest of server uptime: every Bedrock player would be sent a Java resource pack
 * until the next reload. Deferring the check to first use instead means it runs the first time a
 * player actually joins, well after every plugin's {@code onEnable} has already run, while still
 * resolving at most once per instance -- not once per join -- exactly as before.
 */
public final class BedrockDetector {

    private static final String FLOODGATE_PLUGIN_NAME = "floodgate";
    private static final String FLOODGATE_API_CLASS = "org.geysermc.floodgate.api.FloodgateApi";
    private static final String IS_FLOODGATE_PLAYER_METHOD = "isFloodgatePlayer";

    private final Object floodgateApi;
    private final Method isFloodgatePlayerMethod;

    /**
     * {@code true} for an instance built by {@link #create(Logger)} that has not yet resolved
     * whether Floodgate is present. {@code false} for every already-resolved instance, including
     * ones {@link #resolve()} itself produces once resolution completes.
     */
    private final boolean unresolved;

    /** Only meaningful (and only read) while {@link #unresolved} is {@code true}. */
    private final Logger creationLogger;

    /**
     * Caches the resolved delegate the first time {@link #resolve()} runs. Guarded by {@code
     * synchronized} in {@link #resolve()}; see that method's javadoc for why a plain field is
     * enough and {@code volatile} is not relied upon.
     */
    private BedrockDetector resolved;

    private BedrockDetector(Object floodgateApi, Method isFloodgatePlayerMethod) {
        this.floodgateApi = floodgateApi;
        this.isFloodgatePlayerMethod = isFloodgatePlayerMethod;
        this.unresolved = false;
        this.creationLogger = null;
    }

    private BedrockDetector(Logger creationLogger) {
        this.floodgateApi = null;
        this.isFloodgatePlayerMethod = null;
        this.unresolved = true;
        this.creationLogger = creationLogger;
    }

    /**
     * A detector that reports every player as not-Bedrock without ever touching Floodgate. Used
     * directly by tests, and as the resolved fallback whenever Floodgate turns out to be absent or
     * unlinkable.
     */
    public static BedrockDetector alwaysJava() {
        return new BedrockDetector(null, null);
    }

    /**
     * Builds the real detector for a running server. Performs no Floodgate lookup itself -- it
     * only captures {@code logger} for the deferred lookup the first call to {@link
     * #isBedrock(UUID)} triggers. See the class javadoc for why the lookup is deferred rather than
     * happening here.
     */
    public static BedrockDetector create(Logger logger) {
        return new BedrockDetector(logger);
    }

    /**
     * Attempts the reflective link to Floodgate's API, independent of the plugin-manager check so
     * it can be exercised directly in a test with no Bukkit server present. Returns {@code null}
     * on any failure, including a {@code null} {@code FloodgateApi.getInstance()} -- a Floodgate
     * that enables but returns a null instance must be treated exactly like Floodgate being
     * absent, not as a successful link that then NPEs (and is silently swallowed to {@code false})
     * on every subsequent {@link #isBedrock(UUID)} call.
     */
    static BedrockDetector attemptLink(Logger logger) {
        try {
            Class<?> apiClass = Class.forName(FLOODGATE_API_CLASS);
            Object instance = apiClass.getMethod("getInstance").invoke(null);
            if (instance == null) {
                if (logger != null) {
                    logger.warning("Floodgate is enabled but FloodgateApi.getInstance() returned "
                            + "null; treating every player as a Java client");
                }
                return null;
            }
            Method method = apiClass.getMethod(IS_FLOODGATE_PLAYER_METHOD, UUID.class);
            return new BedrockDetector(instance, method);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (logger != null) {
                logger.log(Level.WARNING,
                        "Floodgate is enabled but its API could not be linked reflectively; "
                                + "treating every player as a Java client",
                        e);
            }
            return null;
        }
    }

    /**
     * Resolves this instance to a usable, non-deferred delegate: itself, if already resolved, or
     * the cached (or freshly computed) result of the deferred Floodgate lookup otherwise.
     *
     * <p>Not {@code volatile}/lock-free by design choice, but not lock-free by accident either:
     * every production caller of {@link #isBedrock(UUID)} is a Bukkit event handler reacting to a
     * player-join-shaped event, and Bukkit guarantees those run on the main server thread, so in
     * practice there is never a cross-thread race on {@link #resolved} here. This method is {@code
     * synchronized} anyway rather than relying on that silently: the cost is an uncontended
     * monitor acquisition on the same thread on every call after the first, which is effectively
     * free, and it keeps this class correct even if a future caller ever invokes it off the main
     * thread.
     */
    private synchronized BedrockDetector resolve() {
        if (!unresolved) {
            return this;
        }
        if (resolved == null) {
            resolved = resolveNow();
        }
        return resolved;
    }

    private BedrockDetector resolveNow() {
        if (!Bukkit.getPluginManager().isPluginEnabled(FLOODGATE_PLUGIN_NAME)) {
            return alwaysJava();
        }
        BedrockDetector linked = attemptLink(creationLogger);
        return linked != null ? linked : alwaysJava();
    }

    /**
     * True when {@code playerId} connected through Floodgate. Never throws: a reflective failure
     * at call time resolves to {@code false} (Java client), same as Floodgate being absent.
     */
    public boolean isBedrock(UUID playerId) {
        BedrockDetector target = resolve();
        if (target.isFloodgatePlayerMethod == null) {
            return false;
        }
        try {
            Object result = target.isFloodgatePlayerMethod.invoke(target.floodgateApi, playerId);
            return result instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
