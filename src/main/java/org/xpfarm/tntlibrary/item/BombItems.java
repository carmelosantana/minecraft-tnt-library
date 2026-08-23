/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.item;

import java.util.Optional;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.xpfarm.tntlibrary.core.Keys;

/**
 * Bomb-item identity helper: reads the {@link Keys#TNT_ID} PDC marker back off an {@link ItemStack}.
 *
 * <p>This is the single, authoritative way to decide what bomb (if any) a stack is — the same rule
 * every bomb's {@code createItem()} writes with. Display name and lore are never consulted: both are
 * forgeable by a player in an anvil.
 *
 * <p>Server-dependent (reaches into {@code ItemMeta} / CraftBukkit), so it is not unit-testable
 * headlessly; its behaviour is verified at the runtime gate. The listener layer calls it to detect
 * bomb items in inventories and interactions.
 */
public final class BombItems {

    private BombItems() {}

    /**
     * The bomb id stamped on {@code stack}, or {@link Optional#empty()} if the stack is {@code
     * null}, air/empty, has no {@link ItemMeta}, or carries no {@link Keys#TNT_ID} marker.
     *
     * @param stack the stack to inspect; {@code null} and air are handled without throwing
     * @return the stored bomb id, if present
     */
    public static Optional<String> idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return Optional.ofNullable(pdc.get(Keys.TNT_ID, PersistentDataType.STRING));
    }
}
