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

import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;

/**
 * Builds an Adventure {@link ResourcePackRequest} from plain values.
 *
 * <p>Adventure's {@link ResourcePackInfo}/{@link ResourcePackRequest} are ordinary library
 * classes with no dependency on a running server, so -- unlike {@code Player} or a Bukkit event --
 * this is genuinely unit-testable. It is the one place that protects the {@code .replace(false)}
 * requirement, which is trivial to get wrong silently (e.g. by copying an example that uses
 * {@code Player#setResourcePack} or {@code .replace(true)}, either of which would evict every other
 * server-provided pack from the client and defeat stacking on top of any pack configured in
 * {@code server.properties}).
 *
 * <h2>API choice</h2>
 *
 * <p>This uses the Adventure path ({@link ResourcePackInfo#resourcePackInfo(UUID, URI, String)}
 * and {@link ResourcePackRequest#resourcePackRequest()}), which Paper recommends and is not
 * deprecated -- never {@code Player#setResourcePack(String, byte[], String)}, which Paper's own
 * source soft-deprecates in favour of this path.
 */
public final class ResourcePackRequestFactory {

    private ResourcePackRequestFactory() {}

    /**
     * @param packId this plugin's stable, constant pack UUID -- never a freshly generated one, so
     *     the client and any later status event can be correlated back to this exact pack
     * @param url the pack's https download URL, already validated by {@link
     *     org.xpfarm.tntlibrary.config.TntLibraryConfig}
     * @param sha1 the pack's SHA-1, 40 lowercase hex characters, already validated by {@link
     *     org.xpfarm.tntlibrary.config.TntLibraryConfig}
     * @param prompt player-facing text shown alongside the pack prompt, converted to a plain
     *     {@link Component} internally so callers never need to touch Adventure's text API
     * @param required mirrors {@link
     *     org.xpfarm.tntlibrary.config.TntLibraryConfig#resourcePackRequired()} verbatim; the client
     *     enforces this, not the server -- nothing in this codebase kicks a player over it
     * @return a request with exactly one pack, {@code replace(false)} always, and {@code
     *     required}/{@code prompt} set from the arguments
     * @throws IllegalArgumentException if {@code url} is not a syntactically valid URI
     */
    public static ResourcePackRequest build(
            UUID packId, String url, String sha1, String prompt, boolean required) {
        Objects.requireNonNull(packId, "packId must not be null");
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(sha1, "sha1 must not be null");
        Objects.requireNonNull(prompt, "prompt must not be null");

        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(packId, URI.create(url), sha1);

        return ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .prompt(Component.text(prompt))
                .required(required)
                // Mandatory: replace(true) (and the Player#setResourcePack overloads that imply it)
                // evicts every other server-provided pack from the client, including anything
                // configured server-wide in server.properties. This plugin stacks its pack on top;
                // it never replaces.
                .replace(false)
                .build();
    }
}
