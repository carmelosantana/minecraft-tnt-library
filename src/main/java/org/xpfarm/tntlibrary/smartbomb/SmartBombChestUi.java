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
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * A 27-slot chest GUI that edits one placed Smart Bomb's {@link SmartBombParams} in memory.
 *
 * <p>It is a <em>button panel</em>, not storage: the working copy ({@link #params}) is mutated by
 * {@code −}/{@code +}/toggle clicks (each routed through the clamping {@link SmartBombParams#with*}
 * methods) and only persisted when the player clicks <b>Save</b> — matching the Smart Bomb contract's
 * "Confirm → apply". Because none of these items may ever leave the panel, the owning
 * {@code SmartBombListener} cancels <em>every</em> click and drag on this holder; this class only maps
 * a clicked slot to an outcome and never touches the store itself.
 *
 * <h2>Layout (3 rows of 9)</h2>
 *
 * <pre>
 *   .  R- TNT R+ .  D- CLK D+ .      radius (1..8)      | delay (±20 ticks)
 *   Px pr- SPY pr+ .  Tm t- t+ .     proximity + radius | time-of-day (±1000)
 *   Cn .  .  .  .  .  .  .  Sv       Cancel .......... Save
 * </pre>
 *
 * <p>The Java chest also serves Bedrock players through Geyser, so it is the universal programming
 * fallback. It is a Geyser/Bukkit runtime surface, so it is verified at the runtime gate rather than
 * in JUnit.
 */
public final class SmartBombChestUi implements InventoryHolder {

    /** Rows × 9; a single chest. */
    public static final int SIZE = 27;

    // Slot map — see the class layout diagram.
    private static final int RADIUS_MINUS = 1;
    private static final int RADIUS_DISPLAY = 2;
    private static final int RADIUS_PLUS = 3;
    private static final int DELAY_MINUS = 5;
    private static final int DELAY_DISPLAY = 6;
    private static final int DELAY_PLUS = 7;
    private static final int PROXIMITY_TOGGLE = 9;
    private static final int PROXIMITY_RADIUS_MINUS = 10;
    private static final int PROXIMITY_RADIUS_DISPLAY = 11;
    private static final int PROXIMITY_RADIUS_PLUS = 12;
    private static final int TIME_TOGGLE = 14;
    private static final int TIME_MINUS = 15;
    private static final int TIME_PLUS = 16;
    private static final int CANCEL = 18;
    private static final int SAVE = 26;

    /** Step sizes the ± buttons apply before the params record re-clamps/wraps them. */
    private static final int DELAY_STEP_TICKS = 20;   // one second
    private static final long TIME_STEP_TICKS = 1000L;
    private static final long TIME_DEFAULT = 6000L;    // midday, seeded when the time trigger turns on

    /** What a click on this panel means to the listener that owns it. */
    public enum ClickOutcome { UPDATED, SAVE, CANCEL, IGNORE }

    private final Block block;
    private final SmartBombProgrammer programmer;
    private SmartBombParams params;
    private final Inventory inventory;

    /**
     * Opens an editor over {@code initial} for {@code block}. The {@code programmer} is held only so the
     * listener can reach the single DRY write path on Save; this class never calls it.
     */
    public SmartBombChestUi(Block block, SmartBombParams initial, SmartBombProgrammer programmer) {
        this.block = Objects.requireNonNull(block, "block");
        this.params = Objects.requireNonNull(initial, "initial");
        this.programmer = Objects.requireNonNull(programmer, "programmer");
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Smart Bomb"));
        render();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** The placed block this panel programs; the listener needs it to {@code apply} on Save. */
    public Block block() {
        return block;
    }

    /** The Smart Bomb write path, exposed for the listener's Save handler. */
    public SmartBombProgrammer programmer() {
        return programmer;
    }

    /** The current working copy — the value the listener persists on Save. */
    public SmartBombParams params() {
        return params;
    }

    /**
     * Maps a clicked raw slot to an outcome, mutating the working copy for a ±/toggle click and
     * re-rendering. Save/Cancel are reported back to the listener (which performs the apply/close);
     * every other slot — fillers and read-only displays — is {@link ClickOutcome#IGNORE}d.
     */
    public ClickOutcome onClick(int rawSlot) {
        switch (rawSlot) {
            case RADIUS_MINUS -> params = params.withRadius(params.radius() - 1);
            case RADIUS_PLUS -> params = params.withRadius(params.radius() + 1);
            case DELAY_MINUS -> params = params.withDelayTicks(params.delayTicks() - DELAY_STEP_TICKS);
            case DELAY_PLUS -> params = params.withDelayTicks(params.delayTicks() + DELAY_STEP_TICKS);
            case PROXIMITY_TOGGLE -> params = params.withProximity(!params.proximity());
            case PROXIMITY_RADIUS_MINUS ->
                    params = params.withProximityRadius(params.proximityRadius() - 1);
            case PROXIMITY_RADIUS_PLUS ->
                    params = params.withProximityRadius(params.proximityRadius() + 1);
            case TIME_TOGGLE -> params = params.withTimeTrigger(
                    params.timeTrigger() == null ? TIME_DEFAULT : null);
            case TIME_MINUS -> {
                if (params.timeTrigger() == null) {
                    return ClickOutcome.IGNORE; // stepping is meaningless while the trigger is off
                }
                params = params.withTimeTrigger(params.timeTrigger() - TIME_STEP_TICKS);
            }
            case TIME_PLUS -> {
                if (params.timeTrigger() == null) {
                    return ClickOutcome.IGNORE;
                }
                params = params.withTimeTrigger(params.timeTrigger() + TIME_STEP_TICKS);
            }
            case SAVE -> {
                return ClickOutcome.SAVE;
            }
            case CANCEL -> {
                return ClickOutcome.CANCEL;
            }
            default -> {
                return ClickOutcome.IGNORE; // filler or a read-only display
            }
        }
        render();
        return ClickOutcome.UPDATED;
    }

    /** (Re)populates all 27 slots from the current {@link #params}. */
    private void render() {
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        inventory.setItem(RADIUS_MINUS, named(Material.RED_STAINED_GLASS_PANE, "Radius −1"));
        inventory.setItem(RADIUS_DISPLAY, named(Material.TNT, "Radius: " + params.radius()));
        inventory.setItem(RADIUS_PLUS, named(Material.LIME_STAINED_GLASS_PANE, "Radius +1"));

        inventory.setItem(DELAY_MINUS, named(Material.RED_STAINED_GLASS_PANE, "Delay −1s"));
        inventory.setItem(DELAY_DISPLAY, named(Material.CLOCK, delayLabel()));
        inventory.setItem(DELAY_PLUS, named(Material.LIME_STAINED_GLASS_PANE, "Delay +1s"));

        inventory.setItem(PROXIMITY_TOGGLE, params.proximity()
                ? named(Material.LIME_DYE, "Proximity: ON")
                : named(Material.GRAY_DYE, "Proximity: OFF"));
        inventory.setItem(PROXIMITY_RADIUS_MINUS,
                named(Material.RED_STAINED_GLASS_PANE, "Proximity radius −1"));
        inventory.setItem(PROXIMITY_RADIUS_DISPLAY,
                named(Material.SPYGLASS, "Proximity radius: " + params.proximityRadius()));
        inventory.setItem(PROXIMITY_RADIUS_PLUS,
                named(Material.LIME_STAINED_GLASS_PANE, "Proximity radius +1"));

        inventory.setItem(TIME_TOGGLE, named(Material.DAYLIGHT_DETECTOR, timeLabel()));
        inventory.setItem(TIME_MINUS, named(Material.RED_STAINED_GLASS_PANE, "Time −1000"));
        inventory.setItem(TIME_PLUS, named(Material.LIME_STAINED_GLASS_PANE, "Time +1000"));

        inventory.setItem(CANCEL, named(Material.REDSTONE_BLOCK, "Cancel"));
        inventory.setItem(SAVE, named(Material.EMERALD_BLOCK, "Save"));
    }

    private String delayLabel() {
        return String.format("Delay: %d ticks (≈%.1f s)", params.delayTicks(), params.delayTicks() / 20.0);
    }

    private String timeLabel() {
        return params.timeTrigger() == null
                ? "Time trigger: OFF"
                : "Time trigger: " + params.timeTrigger();
    }

    /** A stack of {@code material} whose display name is {@code label}, with the vanilla italic off. */
    private static ItemStack named(Material material, String label) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }
}
