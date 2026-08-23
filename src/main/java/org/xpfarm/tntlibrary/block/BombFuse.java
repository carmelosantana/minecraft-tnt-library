/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.block;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Runs a placed bomb's fuse. Replaces the old display-entity {@code TntRig.prime}: because a bomb is
 * now a real block, the fuse leaves the block <em>in place</em> so it stays visible on both editions
 * while it burns, then removes it and fires the caller's detonation callback.
 *
 * <h2>Cross-edition feedback</h2>
 *
 * <p>The primed sound and the periodic smoke particle both translate through Geyser, so a Bedrock
 * player sees and hears the same lit fuse as a Java player. No display entity is used (they are
 * invisible to Bedrock), so there is no primed-TNT flash — the honest, identical-on-both-editions
 * cue is sound plus smoke rising off the cube.
 *
 * <h2>Safety</h2>
 *
 * <p>A block can only have one live fuse ({@link #isBurning}); a second ignition is refused. Each tick
 * re-checks that the block is still this bomb — if it was broken or replaced mid-fuse, the fuse aborts
 * with no explosion. All access is on the server main thread (driven by events and the Bukkit
 * scheduler), so the in-progress set needs no synchronisation. Server-dependent; verified at the
 * runtime gate.
 */
public final class BombFuse {

    /** How often the fuse ticks, in server ticks — also the smoke-particle cadence base. */
    private static final long FUSE_PERIOD_TICKS = 2L;

    private final Plugin plugin;

    /** Block cells with a live fuse, so a block cannot be double-ignited. Main-thread only. */
    private final Set<Location> burning = new HashSet<>();

    public BombFuse(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Whether {@code block} already has a live fuse. */
    public boolean isBurning(Block block) {
        return burning.contains(block.getLocation());
    }

    /**
     * Lights the fuse on {@code block}, expected to currently be {@code bombId}'s claimed state. Runs
     * {@code onFuseElapsed} on the main thread after {@code fuseTicks} ticks, once the block has been
     * cleared to air — unless the block stopped being this bomb first (broken/replaced), in which case
     * the fuse aborts silently and {@code onFuseElapsed} never runs.
     *
     * @return {@code true} if a new fuse started; {@code false} if this block was already burning
     */
    public boolean light(Block block, String bombId, int fuseTicks, Runnable onFuseElapsed) {
        Location key = block.getLocation();
        if (!burning.add(key)) {
            return false;
        }
        World world = block.getWorld();
        Location center = key.toCenterLocation();
        world.playSound(center, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                boolean stillThisBomb = BombBlocks.bombIdOf(block)
                        .map(bombId::equals)
                        .orElse(false);
                if (!stillThisBomb) {
                    burning.remove(key);
                    cancel();
                    return; // broken or replaced mid-fuse — no detonation
                }
                if (elapsed >= fuseTicks) {
                    burning.remove(key);
                    cancel();
                    block.setType(Material.AIR, false);
                    onFuseElapsed.run();
                    return;
                }
                world.spawnParticle(Particle.SMOKE, center.clone().add(0, 0.55, 0),
                        3, 0.15, 0.1, 0.15, 0.0);
                elapsed += FUSE_PERIOD_TICKS;
            }
        }.runTaskTimer(plugin, 0L, FUSE_PERIOD_TICKS);
        return true;
    }
}
