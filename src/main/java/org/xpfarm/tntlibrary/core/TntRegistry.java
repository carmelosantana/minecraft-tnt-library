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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Id&rarr;{@link CustomTnt} registry: the single lookup the rest of the plugin uses to resolve a
 * bomb id to its definition.
 *
 * <p>Unlike {@code redstone-stuff}'s {@code ItemRegistry} (a static, compile-time {@code Map.of}),
 * this is a small mutable instance so bombs can be registered at {@code onEnable} — some are
 * config-gated and only some phases ship all six. The trade for mutability is explicit
 * duplicate-id rejection, which this class enforces and {@code TntRegistryTest} pins.
 *
 * <p>Definitions are stored directly (not behind a {@code Supplier}) because a {@link CustomTnt} is
 * itself server-free to construct — only {@link CustomTnt#createItem()} and {@link
 * CustomTnt#detonate} touch the server, and the registry never calls those. That keeps every method
 * here unit-testable with no running server. Insertion order is preserved for stable {@code /list}
 * output.
 *
 * <p>Not thread-safe: registration happens on the main thread at enable time, and lookups on the
 * main thread thereafter.
 */
public final class TntRegistry {

    private final Map<String, CustomTnt> definitions = new LinkedHashMap<>();

    /**
     * Registers {@code tnt} under its own {@link CustomTnt#id()}.
     *
     * @throws NullPointerException if {@code tnt} is {@code null} or its {@link CustomTnt#id()} is
     *     {@code null}
     * @throws IllegalStateException if a bomb is already registered under that id
     */
    public void register(CustomTnt tnt) {
        Objects.requireNonNull(tnt, "tnt");
        String id = Objects.requireNonNull(tnt.id(), "tnt.id()");
        if (definitions.containsKey(id)) {
            throw new IllegalStateException("a bomb is already registered under id '" + id + "'");
        }
        definitions.put(id, tnt);
    }

    /**
     * The definition registered under {@code id}, or empty if none is — including when {@code id}
     * is {@code null}. Never throws for an unknown or {@code null} id.
     */
    public Optional<CustomTnt> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id));
    }

    /** Whether a bomb is registered under {@code id}. Null-safe: {@code null} is never registered. */
    public boolean isRegistered(String id) {
        return id != null && definitions.containsKey(id);
    }

    /** Every registered id, in registration order, as an unmodifiable set. */
    public Set<String> ids() {
        return Collections.unmodifiableSet(definitions.keySet());
    }

    /** Every registered definition, in registration order, as an unmodifiable collection. */
    public Collection<CustomTnt> all() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    /** How many bombs are registered. */
    public int size() {
        return definitions.size();
    }
}
