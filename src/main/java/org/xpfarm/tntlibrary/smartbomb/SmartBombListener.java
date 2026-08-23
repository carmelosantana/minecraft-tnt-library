/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.smartbomb;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.xpfarm.tntlibrary.TntLibraryPlugin;
import org.xpfarm.tntlibrary.block.BombBlocks;
import org.xpfarm.tntlibrary.delivery.BedrockDetector;

/**
 * The Smart Bomb's interaction layer: opening the programming UI on a non-igniting right-click, seeding
 * the store on placement, and cleaning it up on break.
 *
 * <h2>Open-UI precedence (owner-approved)</h2>
 *
 * <p>The interact handler runs at {@link EventPriority#HIGH} with the default {@code ignoreCancelled =
 * false} so it still fires <em>after</em> {@code IgnitionListener} (at {@code NORMAL}) has cancelled the
 * click to suppress the vanilla note cycle. Only the main hand is honoured (the off-hand pass is
 * ignored to avoid a double-open). A right-click on a Smart Bomb then resolves in this order: flint
 * &amp; steel → return (that is the ignite/arm path, owned by {@code IgnitionListener}); sneaking →
 * return (leave vanilla place / do nothing); already armed → return (never reprogram a live,
 * counting-down bomb); otherwise open the programmer.
 *
 * <h2>Bedrock seam</h2>
 *
 * <p>{@link #openBedrockProgrammer} is a private seam: for now it falls back to the Java chest, which
 * already works for Bedrock players through Geyser (the universal fallback). Task 8 will replace its
 * body with a guarded native form. This class references no Floodgate/Cumulus type.
 *
 * <h2>Place/break at MONITOR, ignoreCancelled</h2>
 *
 * <p>Seeding and cleanup run at {@link EventPriority#MONITOR} with {@code ignoreCancelled = true} so
 * they act only on a successful, uncancelled event and only after the shared listeners
 * ({@code PlacementListener}, {@code BombGuardListener}) have finished — the seed sees the block already
 * rewritten to the claimed note state, and break cleanup never fights an earlier veto.
 *
 * <h2>Runtime-only</h2>
 *
 * <p>Every path here needs a live server (events, inventories, block data), so this is verified at the
 * runtime gate rather than in JUnit.
 */
public final class SmartBombListener implements Listener {

    private final TntLibraryPlugin plugin;
    private final SmartBombFeature feature;
    private final BedrockDetector detector;

    public SmartBombListener(TntLibraryPlugin plugin, SmartBombFeature feature, BedrockDetector detector) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.feature = Objects.requireNonNull(feature, "feature");
        this.detector = Objects.requireNonNull(detector, "detector");
    }

    // ---- (a) open the programmer on a non-igniting right-click --------------------------------

    /** A non-igniting right-click on a placed Smart Bomb opens its programming UI. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // main hand only — avoid a double-open from the off-hand pass
        }
        Block block = event.getClickedBlock();
        if (!isSmartBomb(block)) {
            return;
        }
        Player player = event.getPlayer();

        // Precedence: ignite path, sneak, and live bombs are all owned elsewhere / left alone.
        if (player.getInventory().getItemInMainHand().getType() == Material.FLINT_AND_STEEL) {
            return; // ignite/arm path — IgnitionListener owns it
        }
        if (player.isSneaking()) {
            return; // let vanilla place / do nothing — do not open the UI
        }
        if (feature.watcher().isArmed(block)) {
            player.sendActionBar(Component.text("Already armed.", NamedTextColor.RED));
            return; // never reprogram a live, counting-down bomb
        }

        SmartBombParams current = feature.programmer().current(block, feature.seedParams());
        if (detector.isBedrock(player.getUniqueId())) {
            openBedrockProgrammer(player, block, current);
        } else {
            openChest(player, block, current);
        }
    }

    /** Opens the Java chest GUI, which also serves Bedrock players through Geyser. */
    private void openChest(Player player, Block block, SmartBombParams current) {
        SmartBombChestUi ui = new SmartBombChestUi(block, current, feature.programmer());
        player.openInventory(ui.getInventory());
    }

    /**
     * The Bedrock programming seam. Task-7 placeholder: for now it just opens the chest GUI.
     *
     * <p>Task 8: open the native Floodgate CustomForm here (guarded); the chest GUI is the universal
     * fallback and works for Bedrock via Geyser.
     */
    private void openBedrockProgrammer(Player player, Block block, SmartBombParams current) {
        openChest(player, block, current);
    }

    // ---- (b) chest click / drag handling -----------------------------------------------------

    /**
     * Handles a click in the Smart Bomb chest. Every click is cancelled (a button panel never yields its
     * items, including shift-clicks from the player inventory); only top-inventory clicks act.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SmartBombChestUi ui)) {
            return;
        }
        event.setCancelled(true); // cancel ALL movement, always
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return; // ignore clicks in the player's own inventory
        }
        Player player = (Player) event.getWhoClicked();
        switch (ui.onClick(event.getRawSlot())) {
            case SAVE -> {
                ui.programmer().apply(ui.block(), ui.params());
                player.closeInventory();
                player.sendActionBar(Component.text("Smart Bomb programmed.", NamedTextColor.GREEN));
            }
            case CANCEL -> player.closeInventory();
            case UPDATED, IGNORE -> { /* the panel re-rendered itself; nothing else to do */ }
        }
    }

    /** A drag can bypass a click, so drags on the panel are cancelled outright too. */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SmartBombChestUi) {
            event.setCancelled(true);
        }
    }

    // ---- (c) placement seeding ---------------------------------------------------------------

    /** Seeds the store with the placement defaults once, after PlacementListener rewrote the block. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (!isSmartBomb(placed)) {
            return;
        }
        BlockKey key = BlockKey.from(placed);
        if (!feature.store().contains(key)) {
            feature.store().put(key, feature.seedParams()); // don't clobber an existing entry
        }
    }

    // ---- (d) break cleanup -------------------------------------------------------------------

    /** Breaking a Smart Bomb disarms any live watcher and drops its store entry. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isSmartBomb(block)) {
            return;
        }
        feature.watcher().disarm(block);
        feature.store().remove(BlockKey.from(block));
        // BombGuardListener still handles the item drop — do not touch it here.
    }

    /** Whether {@code block} is a placed Smart Bomb (its claimed note-block state). */
    private static boolean isSmartBomb(Block block) {
        return BombBlocks.bombIdOf(block).map(SmartBomb.ID::equals).orElse(false);
    }
}
