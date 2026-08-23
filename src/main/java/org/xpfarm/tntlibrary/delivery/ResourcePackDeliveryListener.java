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

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.xpfarm.tntlibrary.config.TntLibraryConfig;

/**
 * Sends the Java resource pack to Java players on join, and logs how the client responded.
 *
 * <p>Deliberately thin: every decision this class makes is delegated to a pure, plain-value
 * method that a JUnit test can exercise with no server running -- {@link
 * PackDeliveryDecision#shouldSend(boolean, boolean)} decides <em>whether</em> to send, and {@link
 * ResourcePackRequestFactory#build(UUID, String, String, String, boolean)} builds <em>what</em>
 * to send. This class's own job is limited to reading the {@link Player}/{@link TntLibraryConfig}
 * state those methods need and calling Bukkit/Adventure -- which is why it, and only it, cannot be
 * unit-tested (constructing a {@code Player} or firing a Bukkit event both require a running
 * server).
 */
public final class ResourcePackDeliveryListener implements Listener {

    /**
     * This plugin's stable resource-pack UUID, fixed for the plugin's lifetime.
     *
     * <p>Deliberately not a freshly generated random UUID: one constant value lets this exact pack
     * be identified in a later {@link PlayerResourcePackStatusEvent} (compared via {@link
     * PlayerResourcePackStatusEvent#getID()}) and, on a later send, replaced rather than stacked a
     * second time. Derived deterministically from a fixed, namespaced string via {@link
     * UUID#nameUUIDFromBytes(byte[])} rather than a hand-picked literal, so the value is
     * reproducible and auditable from this source line alone -- but it must never be recomputed
     * from a different input string, since that would change the constant it exists to be.
     */
    static final UUID PACK_ID =
            UUID.nameUUIDFromBytes("org.xpfarm.tntlibrary:resource-pack".getBytes(StandardCharsets.UTF_8));

    private final TntLibraryConfig config;
    private final BedrockDetector bedrockDetector;
    private final Logger logger;

    public ResourcePackDeliveryListener(
            TntLibraryConfig config, BedrockDetector bedrockDetector, Logger logger) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.bedrockDetector = Objects.requireNonNull(bedrockDetector, "bedrockDetector must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        sendPack(event.getPlayer());
    }

    /**
     * Sends the Java resource pack to {@code player} if, and only if, {@link
     * PackDeliveryDecision#shouldSend(boolean, boolean)} says to.
     *
     * @return {@code true} if the pack was actually sent; {@code false} if delivery is disabled or
     *     misconfigured, or {@code player} is a Bedrock client
     */
    public boolean sendPack(Player player) {
        Objects.requireNonNull(player, "player must not be null");

        boolean isBedrock = bedrockDetector.isBedrock(player.getUniqueId());
        if (!PackDeliveryDecision.shouldSend(config.packDeliveryEnabled(), isBedrock)) {
            return false;
        }

        ResourcePackRequest request = ResourcePackRequestFactory.build(
                PACK_ID,
                config.resourcePackUrl(),
                config.resourcePackSha1(),
                config.resourcePackPrompt(),
                config.resourcePackRequired());
        player.sendResourcePacks(request);
        return true;
    }

    /**
     * Logs how a client responded to a resource pack, filtered to only our pack -- other plugins,
     * or a server-wide pack from {@code server.properties}, can fire this same event for their own
     * packs, and {@link PlayerResourcePackStatusEvent#getID()} (not {@code getPackId()}, which does
     * not exist on this event) is how we tell them apart.
     *
     * <p>Only {@link Status#SUCCESSFULLY_LOADED} means the pack is actually applied. {@link
     * Status#ACCEPTED} and {@link Status#DOWNLOADED} are intermediate states on the way there and
     * are not logged as success. This never kicks a player: {@code required} is a value handed to
     * the client in the request, and the client -- not this listener -- enforces it.
     */
    @EventHandler
    public void onPlayerResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            return;
        }

        Player player = event.getPlayer();
        Status status = event.getStatus();
        switch (status) {
            case SUCCESSFULLY_LOADED ->
                    logger.info("Resource pack successfully loaded for " + player.getName() + ".");
            case DECLINED ->
                    logger.info(player.getName() + " declined the TNT Library resource pack.");
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD ->
                    logger.warning(player.getName() + " failed to load the TNT Library resource "
                            + "pack (" + status + "). If this repeats across players, check "
                            + "resource-pack.url and resource-pack.sha1 in config.yml.");
            case DISCARDED ->
                    logger.info("TNT Library resource pack was discarded for " + player.getName()
                            + " before it finished loading.");
            case ACCEPTED, DOWNLOADED -> {
                // Intermediate states on the way to SUCCESSFULLY_LOADED, not success themselves;
                // deliberately not logged so a normal join does not produce log noise.
            }
        }
    }
}
