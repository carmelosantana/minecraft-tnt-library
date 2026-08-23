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

import java.util.LinkedHashSet;
import java.util.Set;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.xpfarm.tntlibrary.block.BombFuse;
import org.xpfarm.tntlibrary.command.TntCommand;
import org.xpfarm.tntlibrary.config.TntLibraryConfig;
import org.xpfarm.tntlibrary.core.CustomTnt;
import org.xpfarm.tntlibrary.core.TntRegistry;
import org.xpfarm.tntlibrary.detonation.DetonationListener;
import org.xpfarm.tntlibrary.detonation.Detonator;
import org.xpfarm.tntlibrary.geyser.GeyserAssetInstaller;
import org.xpfarm.tntlibrary.item.BombRecipes;
import org.xpfarm.tntlibrary.item.WaterBomb;
import org.xpfarm.tntlibrary.listener.BombGuardListener;
import org.xpfarm.tntlibrary.listener.IgnitionListener;
import org.xpfarm.tntlibrary.listener.PlacementListener;
import org.xpfarm.tntlibrary.protect.AllowAllProtection;
import org.xpfarm.tntlibrary.protect.ProtectionService;

/**
 * Plugin bootstrap for TNT Library: the Phase-1 wiring that connects the already-built layers so a
 * player can craft, place, ignite, and detonate a Water Bomb.
 *
 * <h2>What is wired here</h2>
 *
 * <p>{@link #onEnable()} builds the runtime graph once — config snapshot, {@link ProtectionService},
 * {@link TntRegistry} (only bombs whose config says {@code enabled}), {@link BombFuse}, {@link
 * Detonator} — then registers the crafting recipes and the four listeners ({@link
 * DetonationListener}, {@link PlacementListener}, {@link IgnitionListener}, {@link BombGuardListener})
 * and the {@code /tntlibrary} command. The mutable services are exposed through package-visible
 * getters so the listeners and command always read the <em>current</em> registry/detonator, which is
 * what lets {@link #reloadPlugin()} swap them without re-registering anything.
 *
 * <p>{@link #onLoad()} runs first, before Geyser initialises, to write the Geyser Custom Blocks assets
 * so a placed bomb renders as a true cube on Bedrock as well as Java.
 *
 * <h2>Phase-1 scope</h2>
 *
 * <p>Only the Water Bomb has a {@link CustomTnt} implementation this phase, so it is the only bomb
 * ever registered even though the config and permissions enumerate all six. A placed bomb is a real
 * {@code note_block} in a claimed state (see {@code org.xpfarm.tntlibrary.block.BombBlocks}), so it
 * ignites with full real-TNT parity — flint &amp; steel, fire/lava, and redstone.
 */
public final class TntLibraryPlugin extends JavaPlugin {

    private TntLibraryConfig config;
    private ProtectionService protection;
    private TntRegistry registry;
    private BombFuse bombFuse;
    private Detonator detonator;

    @Override
    public void onLoad() {
        // Runs before Geyser (via loadbefore) so the custom-block assets exist when Geyser reads them.
        new GeyserAssetInstaller(getLogger(), getFile(), getDataFolder().getParentFile()).install();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = TntLibraryConfig.from(getConfig(), getLogger());
        this.protection = new AllowAllProtection();

        this.registry = new TntRegistry();
        registerEnabledBombs(config);

        this.bombFuse = new BombFuse(this);
        this.detonator = new Detonator(this, config, protection);

        for (CustomTnt bomb : registry.all()) {
            BombRecipes.register(this, bomb);
        }

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new DetonationListener(this, protection), this);
        pm.registerEvents(new PlacementListener(this), this);
        pm.registerEvents(new IgnitionListener(this), this);
        pm.registerEvents(new BombGuardListener(this), this);

        TntCommand command = new TntCommand(this);
        PluginCommand pluginCommand = getCommand("tntlibrary");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        } else {
            getLogger().severe(
                    "Command 'tntlibrary' is missing from plugin.yml — /tntlibrary will not work.");
        }

        getLogger().info("TNTLibrary enabled — " + registry.size() + " bomb(s) registered"
                + (registry.size() > 0 ? " " + registry.ids() : "") + ".");
    }

    @Override
    public void onDisable() {
        // Placed bombs are real blocks and stay in the world across a disable — nothing to sweep.
        // Bukkit cancels this plugin's scheduler tasks (including live fuses) and unregisters its
        // listeners automatically on disable, so only the recipes need explicit teardown.
        if (registry != null) {
            for (CustomTnt bomb : registry.all()) {
                BombRecipes.unregister(this, bomb);
            }
        }
        getLogger().info("TNTLibrary disabled.");
    }

    /**
     * Reloads {@code config.yml} and rebuilds the config-derived services (registry, detonator) and
     * the crafting recipes to match the new enabled set. Listeners and the command need no
     * re-registration because they read the current services through this plugin's getters.
     *
     * @return a human-readable summary of what changed, for the {@code /tntlibrary reload} reply
     */
    public String reloadPlugin() {
        Set<String> before = new LinkedHashSet<>(registry.ids());
        for (CustomTnt bomb : registry.all()) {
            BombRecipes.unregister(this, bomb);
        }

        reloadConfig();
        this.config = TntLibraryConfig.from(getConfig(), getLogger());
        this.registry = new TntRegistry();
        registerEnabledBombs(config);
        this.detonator = new Detonator(this, config, protection);
        for (CustomTnt bomb : registry.all()) {
            BombRecipes.register(this, bomb);
        }

        Set<String> after = new LinkedHashSet<>(registry.ids());
        Set<String> added = new LinkedHashSet<>(after);
        added.removeAll(before);
        Set<String> removed = new LinkedHashSet<>(before);
        removed.removeAll(after);

        StringBuilder summary = new StringBuilder()
                .append("reloaded; ").append(after.size()).append(" bomb(s) registered ").append(after);
        if (!added.isEmpty()) {
            summary.append(", added ").append(added);
        }
        if (!removed.isEmpty()) {
            summary.append(", removed ").append(removed);
        }
        return summary.toString();
    }

    /** Registers every Phase-1 bomb whose config says {@code enabled}. Phase 1 = only the Water Bomb. */
    private void registerEnabledBombs(TntLibraryConfig cfg) {
        if (cfg.bomb(WaterBomb.ID).enabled()) {
            registry.register(new WaterBomb(cfg.bomb(WaterBomb.ID).fuseTicks()));
        }
    }

    /** The live bomb registry; rebuilt by {@link #reloadPlugin()}, so always read it fresh. */
    public TntRegistry registry() {
        return registry;
    }

    /** The shared fuse service that burns a lit bomb block down to detonation; stable across a reload. */
    public BombFuse bombFuse() {
        return bombFuse;
    }

    /** The live detonation entry point; rebuilt by {@link #reloadPlugin()}, so always read it fresh. */
    public Detonator detonator() {
        return detonator;
    }

    /** The current validated configuration snapshot. */
    public TntLibraryConfig config() {
        return config;
    }
}
