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

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.xpfarm.tntlibrary.config.TntLibraryConfig;
import org.xpfarm.tntlibrary.core.CustomTnt;
import org.xpfarm.tntlibrary.protect.ProtectionService;

/**
 * The framework's single detonation entry point: assembles a {@link DetonationContext} for a bomb
 * and fires its effect.
 *
 * <p>This is the seam the fuse layer drives. The rig runs the fuse and, when it elapses, invokes a
 * caller-supplied callback (see {@code TntRig#prime}); the wiring task (T6) makes that callback call
 * {@link #detonate(CustomTnt, Location, Entity)}:
 *
 * <pre>{@code
 * Detonator detonator = new Detonator(plugin, config, protection);   // once, at onEnable
 * tntRig.prime(rig, bomb.fuseTicks(), () -> detonator.detonate(bomb, loc, primer));
 * }</pre>
 *
 * <p>It pulls the bomb's tuned {@link org.xpfarm.tntlibrary.config.BombSettings} from {@code config}
 * by bomb id and pairs them with the shared {@link ProtectionService}, so a bomb's {@code detonate}
 * never has to reach back into configuration itself. The actual boom — a real {@link
 * org.bukkit.entity.TNTPrimed} and, for the Water Bomb, the deferred crater fill — happens inside the
 * bomb and its {@link DetonationListener}; this class only builds the context and delegates.
 */
public final class Detonator {

    private final Plugin plugin;
    private final TntLibraryConfig config;
    private final ProtectionService protection;

    /**
     * @param plugin the owning plugin (scheduling, world access); never {@code null}
     * @param config the live configuration, source of each bomb's {@code BombSettings}; never {@code
     *     null}
     * @param protection the protection seam threaded into every context; never {@code null} — pass an
     *     {@link org.xpfarm.tntlibrary.protect.AllowAllProtection} for the Phase-1 default
     */
    public Detonator(Plugin plugin, TntLibraryConfig config, ProtectionService protection) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.protection = Objects.requireNonNull(protection, "protection");
    }

    /**
     * Detonates {@code bomb} at {@code center}, primed by {@code primer}. Builds the {@link
     * DetonationContext} — settings looked up via {@code config.bomb(bomb.id())}, plus the shared
     * plugin and protection — and invokes {@link CustomTnt#detonate(DetonationContext)}.
     *
     * @param bomb the bomb definition whose effect fires; never {@code null}
     * @param center where it detonates; never {@code null}
     * @param primer the entity that primed it, or {@code null} for non-entity ignition
     */
    public void detonate(CustomTnt bomb, Location center, @Nullable Entity primer) {
        Objects.requireNonNull(bomb, "bomb");
        Objects.requireNonNull(center, "center");
        DetonationContext ctx =
                new DetonationContext(center, primer, plugin, config.bomb(bomb.id()), protection);
        bomb.detonate(ctx);
    }
}
