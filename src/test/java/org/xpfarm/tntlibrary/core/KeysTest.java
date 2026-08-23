/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

/**
 * Guards the plugin's {@link NamespacedKey} constants against the underscore-dropping bug.
 *
 * <p>{@link NamespacedKey#NamespacedKey(org.bukkit.plugin.Plugin, String)} lowercases the plugin
 * name and strips characters outside {@code [a-z0-9._-]} derived from it, which would turn a
 * "TNTLibrary" plugin name into the namespace {@code tntlibrary}. Every key here must instead use
 * the explicit two-arg {@code new NamespacedKey("tnt_library", "...")} form so the namespace keeps
 * the underscore. These assertions fail loudly if any key regresses to the derived form.
 */
final class KeysTest {

    @Test
    void everyKeyUsesTheTntLibraryNamespace() {
        assertEquals("tnt_library", Keys.TNT_ID.getNamespace());
        assertEquals("tnt_library", Keys.MARKER_ROLE.getNamespace());
        assertEquals("tnt_library", Keys.DETONATION_ID.getNamespace());
    }

    @Test
    void everyKeyHasItsExpectedKeyValue() {
        assertEquals("tnt_id", Keys.TNT_ID.getKey());
        assertEquals("marker_role", Keys.MARKER_ROLE.getKey());
        assertEquals("detonation_id", Keys.DETONATION_ID.getKey());
    }
}
