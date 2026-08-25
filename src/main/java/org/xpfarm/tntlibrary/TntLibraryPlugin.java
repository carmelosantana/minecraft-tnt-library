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
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.xpfarm.tntlibrary.block.BombFuse;
import org.xpfarm.tntlibrary.block.PlacedBombIndex;
import org.xpfarm.tntlibrary.command.TntCommand;
import org.xpfarm.tntlibrary.config.BombSettings;
import org.xpfarm.tntlibrary.config.TntLibraryConfig;
import org.xpfarm.tntlibrary.core.CustomTnt;
import org.xpfarm.tntlibrary.core.TntRegistry;
import org.xpfarm.tntlibrary.delivery.BedrockDetector;
import org.xpfarm.tntlibrary.delivery.ResourcePackDeliveryListener;
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
import org.xpfarm.tntlibrary.fbomb.FBomb;
import org.xpfarm.tntlibrary.fbomb.FBombFeature;
import org.xpfarm.tntlibrary.gbomb.GBomb;
import org.xpfarm.tntlibrary.gbomb.GBombFeature;
import org.xpfarm.tntlibrary.smartbomb.SmartBomb;
import org.xpfarm.tntlibrary.smartbomb.SmartBombFeature;
import org.xpfarm.tntlibrary.twins.PlacedTwinIndex;
import org.xpfarm.tntlibrary.twins.TheTwins;
import org.xpfarm.tntlibrary.twins.TwinColor;

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
    private ResourcePackDeliveryListener packDeliveryListener;
    private SmartBombFeature smartBomb;
    private GBombFeature gBomb;
    private FBombFeature fBomb;

    /**
     * The shared, per-world register of placed Twins. Built once at construction and never rebuilt —
     * placed Twins are real blocks that survive a config reload, so a {@code /tntlibrary reload} must
     * not drop the index. Both Twin variant instances registered in {@link #registerEnabledBombs} share
     * this one instance; the placement/break listeners add and remove entries, and {@code
     * TheTwins.detonate} removes a spent pair itself. (A full server reload builds a fresh plugin
     * instance and so a fresh, empty index — the documented cold-start limitation.)
     */
    private final PlacedTwinIndex placedTwinIndex = new PlacedTwinIndex();

    /**
     * The location register the {@link BombGuardListener} uses to heal placed bomb blocks whose
     * note-block instrument has drifted (see {@link PlacedBombIndex}). Self-populating and in-memory;
     * like {@link #placedTwinIndex} it starts empty on a cold start and re-registers each bomb on its
     * first post-restart neighbor update.
     */
    private final PlacedBombIndex placedBombIndex = new PlacedBombIndex();

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
        this.gBomb = new GBombFeature(this, config); // built before registration: registry holds gbomb()
        registerEnabledBombs(config);

        this.bombFuse = new BombFuse(this);
        this.detonator = new Detonator(this, config, protection);

        this.smartBomb = new SmartBombFeature(this, config);
        smartBomb.enable();
        gBomb.enable();
        this.fBomb = new FBombFeature(this, config);
        fBomb.enable();

        for (CustomTnt bomb : registry.all()) {
            BombRecipes.register(this, bomb);
        }

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new DetonationListener(this, protection), this);
        pm.registerEvents(new PlacementListener(this), this);
        pm.registerEvents(new IgnitionListener(this), this);
        pm.registerEvents(new BombGuardListener(this), this);
        registerDeliveryListener();

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
        if (smartBomb != null) {
            smartBomb.disable();
        }
        if (gBomb != null) {
            gBomb.disable(); // mandatory: restores gravity for any entity mid-sequence (Bukkit won't)
        }
        if (fBomb != null) {
            fBomb.disable(); // tears down any in-flight cinematic + sweeps its rig entities
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
        if (gBomb != null) {
            gBomb.disable(); // tear down the old feature (restores gravity) before rebuilding
        }
        this.gBomb = new GBombFeature(this, config); // rebuilt before registration: registry holds gbomb()
        this.registry = new TntRegistry();
        registerEnabledBombs(config);
        this.detonator = new Detonator(this, config, protection);
        registerDeliveryListener();
        if (smartBomb != null) {
            smartBomb.disable();
        }
        this.smartBomb = new SmartBombFeature(this, config);
        smartBomb.enable();
        gBomb.enable();
        if (fBomb != null) {
            fBomb.disable();
        }
        this.fBomb = new FBombFeature(this, config);
        fBomb.enable();
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

    /** Registers every bomb whose config says {@code enabled}. */
    private void registerEnabledBombs(TntLibraryConfig cfg) {
        if (cfg.bomb(WaterBomb.ID).enabled()) {
            registry.register(new WaterBomb(cfg.bomb(WaterBomb.ID).fuseTicks()));
        }
        if (cfg.bomb(TwinColor.BASE_ID).enabled()) {
            // Both Twin variants share the base bombs.twins settings and the ONE placedTwinIndex:
            // fuse-ticks → fuse, radius → beam thickness, the repurposed hang slot → max-pair-distance.
            BombSettings twins = cfg.bomb(TwinColor.BASE_ID);
            registry.register(new TheTwins(
                    TwinColor.WHITE, twins.fuseTicks(), twins.radius(), twins.hangTicks(), placedTwinIndex));
            registry.register(new TheTwins(
                    TwinColor.BLACK, twins.fuseTicks(), twins.radius(), twins.hangTicks(), placedTwinIndex));
        }
        if (cfg.bomb(SmartBomb.ID).enabled()) {
            registry.register(new SmartBomb(cfg.bomb(SmartBomb.ID).fuseTicks()));
        }
        if (cfg.bomb(GBomb.ID).enabled() && gBomb != null) {
            // The registry must hold the feature's runtime-bound instance — a plain new GBomb() has a
            // null runtime and an inert detonate. GBombFeature builds it with the injected fuse/params.
            registry.register(gBomb.gbomb());
        }
        if (cfg.bomb(FBomb.ID).enabled()) {
            // Unlike the G-Bomb, the F-Bomb's cinematic is driven by the ignition divert to the
            // feature's director, so the registry entry is a plain item/recipe carrier.
            registry.register(new FBomb(cfg.bomb(FBomb.ID).fuseTicks()));
        }
    }

    /**
     * (Re)builds the resource-pack delivery listener against the current {@link #config} and
     * registers it, unregistering any previously-registered listener from this plugin first. A fresh
     * listener is built each call, rather than mutated in place, because {@link
     * ResourcePackDeliveryListener}'s config is a {@code final} field set at construction -- so a
     * {@code /tntlibrary reload} that changes the pack URL/SHA-1 is picked up by rebuilding here.
     *
     * <p>{@link BedrockDetector#create(java.util.logging.Logger)} resolves Floodgate lazily on first
     * use (see that class), so building it here does not require Floodgate to have enabled yet.
     */
    private void registerDeliveryListener() {
        if (packDeliveryListener != null) {
            HandlerList.unregisterAll(packDeliveryListener);
        }
        packDeliveryListener = new ResourcePackDeliveryListener(
                config, BedrockDetector.create(getLogger()), getLogger());
        getServer().getPluginManager().registerEvents(packDeliveryListener, this);
    }

    /** The live bomb registry; rebuilt by {@link #reloadPlugin()}, so always read it fresh. */
    public TntRegistry registry() {
        return registry;
    }

    /** The shared fuse service that burns a lit bomb block down to detonation; stable across a reload. */
    public BombFuse bombFuse() {
        return bombFuse;
    }

    /** The shared per-world register of placed Twins; stable across a reload (see the field javadoc). */
    public PlacedTwinIndex placedTwinIndex() {
        return placedTwinIndex;
    }

    /** The location register used to heal drifted bomb blocks; stable across a reload. */
    public PlacedBombIndex placedBombIndex() {
        return placedBombIndex;
    }

    /** The F-Bomb feature (its director drives the ignition-diverted cinematic); null before enable. */
    public FBombFeature fBomb() {
        return fBomb;
    }

    /** The live detonation entry point; rebuilt by {@link #reloadPlugin()}, so always read it fresh. */
    public Detonator detonator() {
        return detonator;
    }

    /** The current validated configuration snapshot. */
    public TntLibraryConfig config() {
        return config;
    }

    /** The Smart Bomb feature module; rebuilt by {@link #reloadPlugin()}, so always read it fresh. */
    public SmartBombFeature smartBomb() {
        return smartBomb;
    }
}
