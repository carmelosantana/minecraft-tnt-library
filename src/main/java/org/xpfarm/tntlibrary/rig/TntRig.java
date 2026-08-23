/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.rig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.xpfarm.tntlibrary.core.CustomTnt;
import org.xpfarm.tntlibrary.core.Keys;

/**
 * Server-side service that represents a <em>placed</em> custom bomb as a pair of display entities: a
 * {@link BlockDisplay} showing a TNT-like cube filling the block cell, and an {@link Interaction}
 * hitbox covering it so a player can later right-click to ignite. Both entities are tagged in their
 * persistent-data container with {@link Keys#RIG_BOMB_ID} (the bomb id) and {@link Keys#RIG_STATE}
 * (placed vs primed), so a rig survives a restart and can be resolved back to its {@link CustomTnt}.
 *
 * <p>Display entities are deliberate: they render on Bedrock through Geyser <em>without</em> a client
 * resource pack (proven in tuesday-twister), so the placed bomb is visible to Java and Bedrock
 * players alike. Nothing here relies on a Java-only client feature or a custom attribute.
 *
 * <h2>What this service does not do</h2>
 *
 * <p>It registers no Bukkit listener and does not wire itself into the plugin — the wiring task owns
 * {@code BlockPlaceEvent}/interact handling and calls this service. It also does not explode: {@link
 * #prime(RigHandle, int, Runnable)} runs the fuse and, when the fuse elapses, invokes the caller's
 * {@code onFuseElapsed} callback. The detonation task supplies the boom in that callback; the blast
 * effect stays out of this package entirely.
 *
 * <h2>Swapping the appearance later</h2>
 *
 * <p>The displayed cube is a placeholder built from {@link Material#TNT} block data via {@link
 * #DISPLAYED_BLOCK}. When the real custom cube texture/model lands with the art, changing that one
 * field (or pointing it at the packed custom block state) is the only edit needed here.
 *
 * <h2>Runtime-only</h2>
 *
 * <p>Every method that spawns, scans for, animates, or removes entities needs a live server and is
 * verified at the runtime gate, not by unit tests. The pure pieces it builds on — {@link RigHandle},
 * {@link RigState}, {@link RigGeometry} — are unit-tested directly.
 */
public final class TntRig {

    /**
     * The placeholder block whose data the rig's {@link BlockDisplay} shows. Swap this one field for
     * the real custom cube's {@link BlockData} when the art lands.
     */
    private static final Material DISPLAYED_BLOCK = Material.TNT;

    /** Horizontal/vertical half-extent (blocks) searched around a cell when locating a rig. */
    private static final double FIND_RADIUS = 0.6;

    /** Peak scale of the primed pulse; the cube swells to this and back to breathe like lit TNT. */
    private static final float PRIMED_PULSE_SCALE = 1.12f;

    /** Ticks between primed-pulse keyframes (each half of the swell). */
    private static final long PRIMED_PULSE_PERIOD = 5L;

    private final Plugin plugin;

    /**
     * Live per-rig scheduler tasks (the primed pulse and the fuse timer), keyed by the rig's
     * {@code BlockDisplay} UUID, so {@link #removeRig(RigHandle)} can cancel a rig's fuse and
     * animation. Concurrent-safe purely as defensive hygiene; all access is on the main thread.
     */
    private final Map<UUID, List<BukkitTask>> tasksByRig = new ConcurrentHashMap<>();

    public TntRig(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    // ---------------------------------------------------------------------------------------------
    // Spawn
    // ---------------------------------------------------------------------------------------------

    /**
     * Spawns a rig for {@code bomb} filling the cell at {@code blockLoc}: a {@link BlockDisplay}
     * showing the bomb cube and an {@link Interaction} covering the block, both tagged {@link
     * RigState#PLACED}. Returns a {@link RigHandle} identifying the new rig.
     *
     * @param blockLoc the placed-block location (normalised to its block corner internally)
     * @param bomb the bomb definition this rig represents
     * @return a handle to the spawned rig
     * @throws IllegalArgumentException if {@code blockLoc} has no world
     */
    public RigHandle spawnRig(Location blockLoc, CustomTnt bomb) {
        Objects.requireNonNull(blockLoc, "blockLoc");
        Objects.requireNonNull(bomb, "bomb");
        World world = blockLoc.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("blockLoc must have a world");
        }
        String bombId = bomb.id();
        Location corner = blockLoc.toBlockLocation();

        BlockDisplay display = world.spawn(corner, BlockDisplay.class, d -> {
            d.setBlock(displayedBlockData());
            d.setTransformation(restingTransform());
            tag(d, bombId, RigState.PLACED);
        });

        Location interactionLoc = corner.clone().add(vec(RigGeometry.interactionOffset()));
        Interaction interaction = world.spawn(interactionLoc, Interaction.class, i -> {
            i.setInteractionWidth(RigGeometry.INTERACTION_WIDTH);
            i.setInteractionHeight(RigGeometry.INTERACTION_HEIGHT);
            i.setResponsive(true);
            tag(i, bombId, RigState.PLACED);
        });

        return new RigHandle(bombId, display.getUniqueId(), interaction.getUniqueId(), corner);
    }

    // ---------------------------------------------------------------------------------------------
    // Identify
    // ---------------------------------------------------------------------------------------------

    /**
     * The bomb id tagged on {@code entity} under {@link Keys#RIG_BOMB_ID}, or {@link Optional#empty()}
     * if the entity is not (or no longer) a tagged rig entity.
     */
    public Optional<String> bombIdOf(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                entity.getPersistentDataContainer().get(Keys.RIG_BOMB_ID, PersistentDataType.STRING));
    }

    /** The {@link RigState} tagged on {@code entity}, or {@link Optional#empty()} if untagged. */
    public Optional<RigState> stateOf(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        String wire =
                entity.getPersistentDataContainer().get(Keys.RIG_STATE, PersistentDataType.STRING);
        return RigState.fromWire(wire);
    }

    /**
     * Finds a placed rig occupying the cell at {@code blockLoc} by scanning the entities around it
     * for the {@link Keys#RIG_BOMB_ID} tag, and rebuilds its {@link RigHandle}. Returns {@link
     * Optional#empty()} if no tagged {@link BlockDisplay} is found there. A rig is recognised by its
     * display; the matching {@link Interaction} (same bomb id, same cell) completes the handle, or a
     * zero UUID stands in if only the display survived.
     */
    public Optional<RigHandle> findRigAt(Location blockLoc) {
        Objects.requireNonNull(blockLoc, "blockLoc");
        World world = blockLoc.getWorld();
        if (world == null) {
            return Optional.empty();
        }
        Location center = blockLoc.toBlockLocation().add(vec(RigGeometry.interactionOffset()));
        BlockDisplay display = null;
        Interaction interaction = null;
        String bombId = null;
        for (Entity entity : world.getNearbyEntities(center, FIND_RADIUS, FIND_RADIUS, FIND_RADIUS)) {
            Optional<String> tagged = bombIdOf(entity);
            if (tagged.isEmpty()) {
                continue;
            }
            if (entity instanceof BlockDisplay bd && display == null) {
                display = bd;
                bombId = tagged.get();
            } else if (entity instanceof Interaction in && interaction == null) {
                interaction = in;
            }
        }
        if (display == null) {
            return Optional.empty();
        }
        UUID interactionId =
                interaction != null ? interaction.getUniqueId() : new UUID(0L, 0L);
        return Optional.of(new RigHandle(bombId, display.getUniqueId(), interactionId,
                blockLoc.toBlockLocation()));
    }

    // ---------------------------------------------------------------------------------------------
    // Prime
    // ---------------------------------------------------------------------------------------------

    /**
     * Primes {@code rig}: flips both entities' {@link Keys#RIG_STATE} to {@link RigState#PRIMED},
     * starts the primed pulse animation, and schedules the fuse. When {@code fuseTicks} have elapsed
     * the rig's animation is stopped and {@code onFuseElapsed} is run on the main thread — that is
     * the moment the detonation task turns into a boom. Detonation is deliberately not performed
     * here.
     *
     * <p>Idempotency is the caller's concern: priming an already-primed rig simply restarts the
     * pulse and fuse. Removing the rig (or the world unloading its entities) before the fuse elapses
     * cancels the scheduled callback via {@link #removeRig(RigHandle)}.
     *
     * @param rig the rig to prime
     * @param fuseTicks fuse length in server ticks — the caller reads this from its {@link CustomTnt}
     *     ({@link CustomTnt#fuseTicks()}); if not positive the callback runs on the next tick
     * @param onFuseElapsed run once when the fuse elapses; must not be {@code null}
     */
    public void prime(RigHandle rig, int fuseTicks, Runnable onFuseElapsed) {
        Objects.requireNonNull(rig, "rig");
        Objects.requireNonNull(onFuseElapsed, "onFuseElapsed");

        BlockDisplay display = blockDisplay(rig);
        Interaction interaction = interaction(rig);
        if (display != null) {
            tag(display, rig.bombId(), RigState.PRIMED);
        }
        if (interaction != null) {
            tag(interaction, rig.bombId(), RigState.PRIMED);
        }

        // Any pre-existing timers on this rig are replaced.
        cancelTasks(rig.blockDisplayId());
        List<BukkitTask> tasks = new ArrayList<>(2);
        if (display != null) {
            tasks.add(startPrimedPulse(display));
        }

        long delay = Math.max(1L, fuseTicks);
        BukkitTask fuse = new BukkitRunnable() {
            @Override
            public void run() {
                cancelTasks(rig.blockDisplayId());
                onFuseElapsed.run();
            }
        }.runTaskLater(plugin, delay);
        tasks.add(fuse);

        tasksByRig.put(rig.blockDisplayId(), tasks);
    }

    /**
     * The primed "breathing" animation: a repeating uniform scale pulse between rest and {@link
     * #PRIMED_PULSE_SCALE}, kept centred in the cell via {@link RigGeometry#centeredScaleTranslation}.
     * This is the swappable visual hook — a shader-driven flash or a colour swap replaces this method
     * without touching the fuse or state logic.
     */
    private BukkitTask startPrimedPulse(BlockDisplay display) {
        return new BukkitRunnable() {
            private boolean swollen;

            @Override
            public void run() {
                if (!display.isValid()) {
                    cancel();
                    return;
                }
                swollen = !swollen;
                float scale = swollen ? PRIMED_PULSE_SCALE : RigGeometry.DISPLAY_SCALE;
                display.setInterpolationDelay(0);
                display.setInterpolationDuration((int) PRIMED_PULSE_PERIOD);
                display.setTransformation(new Transformation(
                        RigGeometry.centeredScaleTranslation(scale), new Quaternionf(),
                        new Vector3f(scale, scale, scale), new Quaternionf()));
            }
        }.runTaskTimer(plugin, 0L, PRIMED_PULSE_PERIOD);
    }

    // ---------------------------------------------------------------------------------------------
    // Remove / cleanup
    // ---------------------------------------------------------------------------------------------

    /**
     * Despawns both of {@code rig}'s entities and cancels any fuse/animation task it owns. Safe to
     * call on a rig whose entities have already gone (they are simply skipped).
     */
    public void removeRig(RigHandle rig) {
        Objects.requireNonNull(rig, "rig");
        cancelTasks(rig.blockDisplayId());
        BlockDisplay display = blockDisplay(rig);
        if (display != null) {
            display.remove();
        }
        Interaction interaction = interaction(rig);
        if (interaction != null) {
            interaction.remove();
        }
    }

    /**
     * Removes every stray rig entity (anything tagged with {@link Keys#RIG_BOMB_ID}) in {@code world}
     * — the onEnable cleanup that clears rigs stranded by a crash before the plugin could remove
     * them, mirroring tuesday-twister's orphan sweep. Returns the number of entities removed.
     */
    public int cleanupOrphans(World world) {
        Objects.requireNonNull(world, "world");
        int removed = 0;
        for (Entity entity : world.getEntities()) {
            if (entity.getPersistentDataContainer().has(Keys.RIG_BOMB_ID, PersistentDataType.STRING)) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    /**
     * Runs {@link #cleanupOrphans(World)} across every loaded world. Intended for plugin onEnable.
     * Returns the total number of entities removed.
     */
    public int cleanupOrphans() {
        int removed = 0;
        for (World world : plugin.getServer().getWorlds()) {
            removed += cleanupOrphans(world);
        }
        return removed;
    }

    // ---------------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------------

    /** The block data the rig's display shows. Swap {@link #DISPLAYED_BLOCK} to change the cube. */
    private static BlockData displayedBlockData() {
        return DISPLAYED_BLOCK.createBlockData();
    }

    private static Transformation restingTransform() {
        return new Transformation(RigGeometry.displayTranslation(), new Quaternionf(),
                new Vector3f(RigGeometry.DISPLAY_SCALE, RigGeometry.DISPLAY_SCALE,
                        RigGeometry.DISPLAY_SCALE),
                new Quaternionf());
    }

    private static void tag(Entity entity, String bombId, RigState state) {
        entity.getPersistentDataContainer().set(Keys.RIG_BOMB_ID, PersistentDataType.STRING, bombId);
        entity.getPersistentDataContainer().set(Keys.RIG_STATE, PersistentDataType.STRING,
                state.wire());
    }

    private BlockDisplay blockDisplay(RigHandle rig) {
        Entity entity = plugin.getServer().getEntity(rig.blockDisplayId());
        return entity instanceof BlockDisplay display ? display : null;
    }

    private Interaction interaction(RigHandle rig) {
        Entity entity = plugin.getServer().getEntity(rig.interactionId());
        return entity instanceof Interaction interaction ? interaction : null;
    }

    private void cancelTasks(UUID rigDisplayId) {
        List<BukkitTask> tasks = tasksByRig.remove(rigDisplayId);
        if (tasks != null) {
            for (BukkitTask task : tasks) {
                task.cancel();
            }
        }
    }

    private static org.bukkit.util.Vector vec(Vector3f v) {
        return new org.bukkit.util.Vector(v.x(), v.y(), v.z());
    }
}
