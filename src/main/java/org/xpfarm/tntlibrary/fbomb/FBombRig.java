/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.fbomb;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Builds and animates the F-Bomb's visible "fake-Wither" apparition out of display entities: a dark
 * central core, three purple-eyed heads arranged around it, and a single non-responsive {@link
 * Interaction} entity that gives the apparition a presence/hitbox. Vanilla and Bedrock/Geyser
 * clients alike see only ordinary display and interaction entities, so the whole "mob" is assembled
 * here rather than as a real hostile entity — no resource pack and no real mob is required.
 *
 * <p>This is a deliberately simplified sibling of {@code tuesday-twister}'s {@code StormRig}: a
 * single static hovering figure with no growth phases and no tentacles. The {@link Interaction} is
 * presence-only — {@link Interaction#setResponsive(boolean)} is set to {@code false}, so it takes no
 * damage and deals no melee; this class never reads damage/attack events from it.
 *
 * <p>Server-dependent (spawns/animates/removes display entities); verified at the runtime gate 12,
 * not in JUnit.
 */
public final class FBombRig {

    /** Tags every entity this rig spawns with the owning show's id, for the director's orphan sweep. */
    public static final NamespacedKey RIG_KEY = new NamespacedKey("tnt_library", "fbomb_rig");

    /** How the head displays are placed on the horizontal ring around the core, in radians. */
    private static final double[] HEAD_ANGLES = {0.0, 2.0 * Math.PI / 3.0, 4.0 * Math.PI / 3.0};

    /** Horizontal radius (blocks) at which the heads orbit the core. */
    private static final double HEAD_RADIUS = 1.2;

    /** Height (blocks) the heads sit above the core. */
    private static final double HEAD_HEIGHT = 0.8;

    /** Uniform scale of the central core display. */
    private static final float CORE_SCALE = 1.0f;

    /** Uniform scale of each head display. */
    private static final float HEAD_SCALE = 0.8f;

    /** Uniform scale of each glowing eye display. */
    private static final float EYE_SCALE = 0.25f;

    /** Interaction hitbox width/height, matching a roughly Wither-sized presence. */
    private static final float INTERACTION_WIDTH = 2.0f;

    private static final float INTERACTION_HEIGHT = 3.0f;

    private final Plugin plugin;
    private final UUID showId;

    private BlockDisplay core;
    private final List<BlockDisplay> heads = new ArrayList<>();
    private final List<BlockDisplay> eyes = new ArrayList<>();
    private Interaction presence;

    public FBombRig(Plugin plugin, UUID showId) {
        this.plugin = plugin;
        this.showId = showId;
    }

    /**
     * Spawns every part of the rig at {@code center}: the core, three heads with eyes, and the
     * presence {@link Interaction}. Every spawned entity is tagged with {@link #RIG_KEY} so the
     * director's onEnable orphan-sweep can reap it after a crash.
     */
    public void spawn(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return; // detached location; nothing to spawn against
        }

        core = spawnDisplay(world, center, Material.CRYING_OBSIDIAN, new Vector3f(), CORE_SCALE);

        for (double angle : HEAD_ANGLES) {
            Vector3f offset = headOffset(angle, 0.0);
            BlockDisplay head = spawnDisplay(world, center, Material.OBSIDIAN, offset, HEAD_SCALE);
            heads.add(head);

            Vector3f eyeOffset = eyeOffset(angle, 0.0);
            BlockDisplay eye =
                    spawnDisplay(world, center, Material.PURPLE_STAINED_GLASS, eyeOffset, EYE_SCALE);
            eye.setBrightness(new Display.Brightness(15, 15));
            eyes.add(eye);
        }

        presence = world.spawn(center, Interaction.class, i -> {
            i.setInteractionWidth(INTERACTION_WIDTH);
            i.setInteractionHeight(INTERACTION_HEIGHT);
            i.setResponsive(false);
            i.getPersistentDataContainer().set(RIG_KEY, PersistentDataType.STRING, showId.toString());
        });
    }

    /**
     * A cheap hover bob: recomputes a small vertical offset from {@code tick} and re-applies every
     * part's transform relative to {@code center}, teleporting the whole rig there first.
     *
     * <p>Because a {@code Transformation}'s translation is relative to each entity's own location,
     * every display is teleported to {@code center} before its local offset is (re)applied — mirrors
     * {@code StormRig.applyTransform}.
     */
    public void animate(Location center, long tick) {
        if (core == null) {
            return;
        }
        double dy = Math.sin(tick * 0.15) * 0.15;

        applyTransform(core, center, new Vector3f(0.0f, (float) dy, 0.0f), CORE_SCALE);

        for (int i = 0; i < heads.size(); i++) {
            applyTransform(heads.get(i), center, headOffset(HEAD_ANGLES[i], dy), HEAD_SCALE);
        }
        for (int i = 0; i < eyes.size(); i++) {
            applyTransform(eyes.get(i), center, eyeOffset(HEAD_ANGLES[i], dy), EYE_SCALE);
        }
        if (presence != null) {
            presence.teleport(center);
        }
    }

    /** Removes every entity that makes up this rig. Null-safe and idempotent. */
    public void remove() {
        if (core != null) {
            core.remove();
            core = null;
        }
        removeAll(heads);
        removeAll(eyes);
        if (presence != null) {
            presence.remove();
            presence = null;
        }
    }

    /** The UUIDs of every currently-live entity in this rig. */
    public List<UUID> entityIds() {
        List<UUID> ids = new ArrayList<>();
        if (core != null) {
            ids.add(core.getUniqueId());
        }
        for (BlockDisplay head : heads) {
            ids.add(head.getUniqueId());
        }
        for (BlockDisplay eye : eyes) {
            ids.add(eye.getUniqueId());
        }
        if (presence != null) {
            ids.add(presence.getUniqueId());
        }
        return ids;
    }

    private Vector3f headOffset(double angle, double dy) {
        return new Vector3f((float) (Math.cos(angle) * HEAD_RADIUS), (float) (HEAD_HEIGHT + dy),
                (float) (Math.sin(angle) * HEAD_RADIUS));
    }

    private Vector3f eyeOffset(double angle, double dy) {
        return headOffset(angle, dy)
                .add((float) (Math.cos(angle) * 0.1), 0.2f, (float) (Math.sin(angle) * 0.1));
    }

    private BlockDisplay spawnDisplay(World world, Location center, Material material,
            Vector3f offset, float scale) {
        BlockDisplay display = world.spawn(center, BlockDisplay.class, d -> {
            d.setBlock(material.createBlockData());
            d.getPersistentDataContainer().set(RIG_KEY, PersistentDataType.STRING, showId.toString());
        });
        display.setTransformation(new Transformation(offset, new Quaternionf(),
                new Vector3f(scale, scale, scale), new Quaternionf()));
        return display;
    }

    private void applyTransform(BlockDisplay display, Location center, Vector3f offset, float scale) {
        display.teleport(center);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(2);
        display.setTransformation(new Transformation(offset, new Quaternionf(),
                new Vector3f(scale, scale, scale), new Quaternionf()));
    }

    private static void removeAll(List<BlockDisplay> displays) {
        for (BlockDisplay display : displays) {
            display.remove();
        }
        displays.clear();
    }
}
