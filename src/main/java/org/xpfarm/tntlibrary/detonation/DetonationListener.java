/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.detonation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.xpfarm.tntlibrary.config.BombType;
import org.xpfarm.tntlibrary.core.Keys;
import org.xpfarm.tntlibrary.protect.ProtectionService;

/**
 * Catches the explosions this plugin spawns and turns the Water Bomb's crater into a still pool.
 *
 * <p>This class only <em>defines</em> the behaviour; it registers nothing. The wiring task (T6)
 * constructs one instance and registers it as a Bukkit listener at {@code onEnable}.
 *
 * <h2>Why a real explosion + this listener</h2>
 *
 * <p>A bomb detonates by spawning a genuine {@link org.bukkit.entity.TNTPrimed} tagged in its PDC
 * with {@link Keys#DETONATION_ID} (see {@code WaterBomb#detonate}). Using a real entity — rather than
 * {@code World#createExplosion} — is deliberate: WorldGuard and GriefPrevention already listen for
 * {@link EntityExplodeEvent} and strip protected blocks out of {@link EntityExplodeEvent#blockList()}
 * before this handler runs. This listener therefore observes a {@code blockList} that is already the
 * <b>legal</b> crater — protected terrain was never in it — which is exactly the set of cells it is
 * allowed to flood. That is how "respect region protection" is satisfied for the destruction and the
 * fill with no region-integration code. To see that filtered list, this handler runs at {@link
 * EventPriority#HIGHEST} with {@code ignoreCancelled = true}, after the protection plugins and only
 * when the blast was not cancelled outright.
 *
 * <h2>Deferred: protected-air water placement</h2>
 *
 * <p>The one honest limitation of the crater-only design: the fill can only place water in cells the
 * explosion already cleared, so it never floods protected terrain — but it also does not consult a
 * region API before placing water in unprotected air <em>inside</em> a protected region's airspace.
 * A future region-aware {@link ProtectionService#canPlace(Location)} closes that gap without touching
 * this class; it is already consulted per cell below.
 *
 * <h2>Timing</h2>
 *
 * <p>The crater cells are snapshotted from {@code blockList()} <em>during</em> the event (they are
 * still solid then); the water is placed one tick later, once the server has actually broken those
 * blocks to air. The rim/level math is delegated to the server-free {@link CraterMath}; only the
 * height reads and block writes here need a live world, which is why this class is verified at the
 * runtime gate rather than in JUnit.
 *
 * <h2>Runtime-only</h2>
 *
 * <p>Every path here needs a running server (events, world height reads, block sets). Its pure
 * collaborator {@link CraterMath} carries the unit tests.
 */
public final class DetonationListener implements Listener {

    private final Plugin plugin;
    private final ProtectionService protection;

    /**
     * @param plugin the owning plugin, for scheduling the one-tick-later fill; never {@code null}
     * @param protection the seam consulted before each water source is placed; never {@code null}
     */
    public DetonationListener(Plugin plugin, ProtectionService protection) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.protection = Objects.requireNonNull(protection, "protection");
    }

    /**
     * Snapshots the crater of one of our tagged explosions and schedules the Water Bomb fill. Ignores
     * any explosion this plugin did not spawn (untagged) and any non-water tagged bomb (Phase 1 only
     * the Water Bomb floods; other bombs may tag their explosions for their own effects later).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (entity == null) {
            return;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        String bombId = pdc.get(Keys.DETONATION_ID, PersistentDataType.STRING);
        if (bombId == null) {
            return; // not one of ours
        }
        if (!BombType.WATERBOMB.id().equals(bombId)) {
            return; // tagged, but not a flooding bomb this phase
        }

        World world = entity.getWorld();
        if (world.getEnvironment() == World.Environment.NETHER) {
            // Water evaporates in the Nether: skip the fill entirely, leaving just the crater.
            plugin.getLogger().fine(() ->
                    "Water Bomb crater fill skipped in the Nether at " + event.getLocation());
            return;
        }

        // The blocks are still solid now; capture their coordinates before they break to air.
        List<CraterMath.Cell> crater = new ArrayList<>(event.blockList().size());
        for (Block block : event.blockList()) {
            crater.add(new CraterMath.Cell(block.getX(), block.getY(), block.getZ()));
        }
        if (crater.isEmpty()) {
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(
                plugin, () -> floodCrater(world, crater), 1L);
    }

    /**
     * Places permanent water source blocks in every crater cell at or below the surrounding rim.
     * Runs one tick after the explosion, so the crater cells are now air. The rim and the submerged
     * subset are computed by {@link CraterMath}; each candidate cell is skipped if it is no longer
     * empty or if {@link ProtectionService#canPlace(Location)} refuses it. Water placed here is a
     * real, permanent terrain change — nothing tracks or cleans it up.
     */
    private void floodCrater(World world, List<CraterMath.Cell> crater) {
        CraterMath.SurfaceHeightFn heights =
                (x, z) -> OptionalInt.of(world.getHighestBlockYAt(x, z));
        List<CraterMath.Cell> toFill = CraterMath.floodCells(crater, heights);

        int placed = 0;
        for (CraterMath.Cell cell : toFill) {
            Block block = world.getBlockAt(cell.x(), cell.y(), cell.z());
            if (!block.getType().isAir()) {
                continue; // only fill the cleared cavity, never solids that survived the blast
            }
            Location loc = block.getLocation();
            if (!protection.canPlace(loc)) {
                continue;
            }
            block.setType(Material.WATER, true); // a full source block (level 0)
            placed++;
        }

        int total = placed;
        plugin.getLogger().fine(() ->
                "Water Bomb filled " + total + " crater cell(s) with water in " + world.getName());
    }
}
