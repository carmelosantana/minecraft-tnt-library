/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.core;

import org.bukkit.NamespacedKey;

/**
 * Every {@link NamespacedKey} this plugin writes into a {@link
 * org.bukkit.persistence.PersistentDataContainer}, in one place.
 *
 * <h2>Why the explicit two-arg constructor</h2>
 *
 * <p>Each key is built as {@code new NamespacedKey("tnt_library", "...")} on purpose. The
 * plugin-arg form {@link NamespacedKey#NamespacedKey(org.bukkit.plugin.Plugin, String)} derives the
 * namespace from the plugin name by lowercasing it and dropping characters outside {@code
 * [a-z0-9._-]} — that turns "TNTLibrary" into {@code tntlibrary}, silently dropping the underscore.
 * The namespace persisted on every tagged item and entity must read exactly {@code tnt_library}, so
 * the string form is the only correct one here. {@code KeysTest} pins this.
 *
 * <p>These constants need no plugin instance and never touch a running server, so they are safe to
 * reference from unit tests. This is a pure constant holder — not a service locator.
 */
public final class Keys {

    /** Namespace shared by every key below. Must stay exactly {@code tnt_library}. */
    public static final String NAMESPACE = "tnt_library";

    /**
     * Marks a bomb <em>item</em> with its stable string bomb id, e.g. {@code waterbomb}. This is the
     * identity read back to decide what a stack actually is — display name and lore are forgeable and
     * never used for identity. A <em>placed</em> bomb is identified differently: it is a real vanilla
     * {@code note_block} in a claimed blockstate (see {@code org.xpfarm.tntlibrary.block.BombBlocks}),
     * so no PDC marker is involved once it is in the world.
     */
    public static final NamespacedKey TNT_ID = new NamespacedKey(NAMESPACE, "tnt_id");

    /**
     * Reserved for the future Twins bomb: the role a marker entity plays within a multi-entity
     * detonation (e.g. which twin it is). Declared now so the block and detonation layers share one
     * key when the Twins arrive.
     */
    public static final NamespacedKey MARKER_ROLE = new NamespacedKey(NAMESPACE, "marker_role");

    /**
     * Marks the real {@link org.bukkit.entity.TNTPrimed} entity a bomb spawns to detonate, carrying
     * the bomb's id as its value. The detonation layer's {@code EntityExplodeEvent} listener reads
     * this back to recognise its own explosions (versus vanilla TNT) and to dispatch the right
     * post-blast effect — e.g. the Water Bomb's crater fill. Distinct from {@link #TNT_ID} (which
     * marks an inventory item): this marks the transient primed-explosive entity for the single tick
     * it lives.
     */
    public static final NamespacedKey DETONATION_ID = new NamespacedKey(NAMESPACE, "detonation_id");

    private Keys() {}
}
