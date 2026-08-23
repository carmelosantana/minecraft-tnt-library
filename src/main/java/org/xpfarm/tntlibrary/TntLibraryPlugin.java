/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin bootstrap for TNT Library.
 *
 * <p>This is the gate-2/3 scaffold: it establishes the buildable plugin skeleton, its descriptor,
 * and its configuration. The custom-TNT framework — the {@code CustomTnt} definition, the
 * {@code TntRegistry}, the display-entity placement rig, and the individual bombs — is built at the
 * dev gate. Bedrock/Geyser resource-pack assets are installed in {@link #onLoad()} (Geyser reads its
 * {@code custom_mappings/} during its own {@code onEnable}), so that hook is reserved here even
 * though it currently does nothing.
 */
public final class TntLibraryPlugin extends JavaPlugin {

    @Override
    public void onLoad() {
        // Reserved: Bedrock/Geyser asset install runs here (before any plugin's onEnable). Dev gate.
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("TNTLibrary enabled (scaffold) — no bombs registered yet.");
    }

    @Override
    public void onDisable() {
        getLogger().info("TNTLibrary disabled.");
    }
}
