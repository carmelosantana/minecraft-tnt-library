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

import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.response.CustomFormResponse;
import org.geysermc.floodgate.api.FloodgateApi;
import org.xpfarm.tntlibrary.block.BombBlocks;

/**
 * The native Bedrock programming UI for a placed Smart Bomb: a Floodgate/Cumulus {@link CustomForm}.
 *
 * <h2>The ONLY Cumulus/Floodgate-touching class</h2>
 *
 * <p>Every {@code org.geysermc.*} (Cumulus/Floodgate) reference in the Smart Bomb feature lives here and
 * nowhere else. {@link SmartBombListener} names this class from exactly one guarded call site, so the
 * JVM lazily links these Cumulus types only the first time {@link #open} actually executes — which only
 * happens for a Bedrock player on a Floodgate-enabled server. A pure-Java server never reaches that call
 * and therefore never classloads a Cumulus type. This is runtime-only code (a live Floodgate API and a
 * connected Bedrock client), so it is verified at the runtime gate rather than in JUnit.
 *
 * <h2>Off-main-thread callback</h2>
 *
 * <p>Floodgate delivers the form response on a Netty thread, not the server main thread. The programmer
 * and its store are main-thread-only, so the result handler hops back via
 * {@code plugin.getServer().getScheduler().runTask(...)} before touching the programmer.
 */
public final class SmartBombBedrockForm {

    private SmartBombBedrockForm() {
        // static entry point only
    }

    /**
     * Builds and sends the Bedrock programming form. Reads {@code current} to seed each component, and on
     * a valid submit hops back to the main thread to persist the new programming (re-checking the block
     * is still a Smart Bomb first).
     */
    public static void open(Plugin plugin, Player player, Block block, SmartBombParams current,
            SmartBombProgrammer programmer) {
        FloodgateApi api = FloodgateApi.getInstance();
        if (api == null) {
            plugin.getLogger().fine("FloodgateApi unavailable; skipping Bedrock Smart Bomb form");
            return;
        }

        float delaySeconds = clamp(current.delayTicks() / 20f, 1f, 300f);
        float timeValue = current.timeTrigger() == null ? 6000f : (float) (long) current.timeTrigger();

        CustomForm form = CustomForm.builder()
                .title("Smart Bomb")
                .slider("Radius (blocks)", 1, 8, 1, current.radius())          // 0
                .slider("Delay (seconds)", 1, 300, 1, delaySeconds)            // 1
                .toggle("Time-of-day trigger", current.timeTrigger() != null)  // 2
                .slider("Time of day (0-23999)", 0, 23999, 100, timeValue)     // 3
                .toggle("Proximity trigger", current.proximity())              // 4
                .slider("Proximity radius (blocks)", 1, 16, 1, current.proximityRadius()) // 5
                .validResultHandler((CustomFormResponse response) -> {
                    int radius = Math.round(response.asSlider(0));
                    int delayTicks = Math.round(response.asSlider(1)) * 20;
                    boolean timeOn = response.asToggle(2);
                    int timeVal = Math.round(response.asSlider(3));
                    boolean proxOn = response.asToggle(4);
                    int proxRadius = Math.round(response.asSlider(5));

                    SmartBombParams programmed = new SmartBombParams(
                            radius, delayTicks, timeOn ? (long) timeVal : null, proxOn, proxRadius);

                    // Hop back to the main thread: the callback fires on a Netty thread, but the
                    // programmer and its store are main-thread-only.
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (BombBlocks.bombIdOf(block).map(SmartBomb.ID::equals).orElse(false)) {
                            programmer.apply(block, programmed);
                            player.sendActionBar(Component.text("Smart Bomb programmed."));
                        }
                    });
                })
                .build();

        api.sendForm(player.getUniqueId(), form);
    }

    private static float clamp(float value, float lo, float hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
