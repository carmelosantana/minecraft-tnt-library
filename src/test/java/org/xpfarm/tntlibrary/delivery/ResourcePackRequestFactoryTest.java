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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.UUID;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ResourcePackRequestFactory}. Adventure's {@link ResourcePackInfo}/{@link
 * ResourcePackRequest} are plain library classes with no server dependency, so these run with no
 * Bukkit server present. This is the test that directly protects the {@code .replace(false)}
 * requirement -- the single easiest thing here to get wrong silently.
 */
final class ResourcePackRequestFactoryTest {

    private static final UUID PACK_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String URL = "https://example.com/pack.zip";
    private static final String SHA1 = "a".repeat(40);
    private static final String PROMPT = "TNT Library adds custom explosive textures.";

    @Test
    void replaceIsAlwaysFalse() {
        ResourcePackRequest request = ResourcePackRequestFactory.build(PACK_ID, URL, SHA1, PROMPT, false);

        assertFalse(request.replace(), "replace(true) would evict every other server-provided pack");
    }

    @Test
    void replaceIsFalseEvenWhenRequiredIsTrue() {
        // required and replace are independent knobs; make sure a true `required` never leaks
        // into forcing replace(true) too.
        ResourcePackRequest request = ResourcePackRequestFactory.build(PACK_ID, URL, SHA1, PROMPT, true);

        assertFalse(request.replace());
    }

    @Test
    void requiredMatchesArgument() {
        assertTrue(ResourcePackRequestFactory.build(PACK_ID, URL, SHA1, PROMPT, true).required());
        assertFalse(ResourcePackRequestFactory.build(PACK_ID, URL, SHA1, PROMPT, false).required());
    }

    @Test
    void packListHasExactlyOneEntryWithTheGivenIdUriAndHash() {
        ResourcePackRequest request = ResourcePackRequestFactory.build(PACK_ID, URL, SHA1, PROMPT, false);

        assertEquals(1, request.packs().size());
        ResourcePackInfo info = request.packs().get(0);
        assertEquals(PACK_ID, info.id());
        assertEquals(URI.create(URL), info.uri());
        assertEquals(SHA1, info.hash());
    }

    @Test
    void promptIsCarriedThroughAsAComponent() {
        ResourcePackRequest request = ResourcePackRequestFactory.build(PACK_ID, URL, SHA1, PROMPT, false);

        assertEquals(Component.text(PROMPT), request.prompt());
    }

    @Test
    void malformedUrlThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> ResourcePackRequestFactory.build(PACK_ID, "not a uri", SHA1, PROMPT, false));
    }

    @Test
    void nullArgumentsThrowNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> ResourcePackRequestFactory.build(null, URL, SHA1, PROMPT, false));
        assertThrows(NullPointerException.class,
                () -> ResourcePackRequestFactory.build(PACK_ID, null, SHA1, PROMPT, false));
        assertThrows(NullPointerException.class,
                () -> ResourcePackRequestFactory.build(PACK_ID, URL, null, PROMPT, false));
        assertThrows(NullPointerException.class,
                () -> ResourcePackRequestFactory.build(PACK_ID, URL, SHA1, null, false));
    }
}
