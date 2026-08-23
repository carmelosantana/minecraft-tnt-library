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

/**
 * The whole "should we send this player the Java resource pack?" decision, expressed as a pure
 * function of two plain booleans.
 *
 * <p>Deliberately takes no {@code Player}, config object, or Bukkit type: everything that could
 * ever influence the decision -- whether pack delivery is configured and enabled ({@link
 * org.xpfarm.tntlibrary.config.TntLibraryConfig#packDeliveryEnabled()}) and whether the player is
 * connecting through Floodgate -- is resolved by the caller and handed in as a value. That is what
 * makes this class exercisable by a plain JUnit test with no server running.
 */
public final class PackDeliveryDecision {

    private PackDeliveryDecision() {}

    /**
     * @param packDeliveryEnabled the config's derived {@code packDeliveryEnabled()}: {@code false}
     *     whenever the url/sha1 are missing or invalid
     * @param isBedrockPlayer whether the player is connected through Floodgate. Bedrock players are
     *     always skipped: Geyser serves them their own Bedrock pack, and a Java pack URL/hash is
     *     meaningless to a Bedrock client
     * @return {@code true} only when the pack is configured for delivery and the player is a Java
     *     client
     */
    public static boolean shouldSend(boolean packDeliveryEnabled, boolean isBedrockPlayer) {
        return packDeliveryEnabled && !isBedrockPlayer;
    }
}
