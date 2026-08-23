/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Parses the shipped resource YAML with the same SnakeYAML the server uses.
 *
 * <h2>Why this exists</h2>
 *
 * <p>A malformed {@code plugin.yml} is not a compile error, is not a test failure, and does not
 * fail {@code mvn verify} — Maven copies the file into the JAR and it is only parsed when a real
 * Paper server boots. A descriptor Paper cannot parse makes the plugin absent from {@code /plugins}
 * rather than present-and-disabled, a materially more confusing symptom. These tests close that gap
 * at gate 6 instead of gate 7a.
 */
final class PluginDescriptorTest {

    private static final Path PLUGIN_YML = descriptor("plugin.yml");
    private static final Path CONFIG_YML = descriptor("config.yml");

    /**
     * Prefers the Maven-filtered copy in {@code target/classes} — that is the file that actually
     * ships, and property substitution can inject YAML metacharacters the source file never had.
     * Falls back to the source tree so the test still runs before {@code process-resources}.
     */
    private static Path descriptor(String name) {
        Path filtered = Path.of("target", "classes", name);
        return Files.exists(filtered) ? filtered : Path.of("src", "main", "resources", name);
    }

    private static Map<String, Object> parse(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return new Yaml().load(in);
        }
    }

    @Test
    void pluginYmlIsValidYaml() throws IOException {
        assertNotNull(parse(PLUGIN_YML), "plugin.yml parsed to null — the file is empty or malformed");
    }

    @Test
    void configYmlIsValidYaml() throws IOException {
        assertNotNull(parse(CONFIG_YML), "config.yml parsed to null — the file is empty or malformed");
    }

    @Test
    void pluginYmlDeclaresTheFieldsPaperRequires() throws IOException {
        Map<String, Object> parsed = parse(PLUGIN_YML);

        assertEquals("TNTLibrary", parsed.get("name"));
        assertEquals("org.xpfarm.tntlibrary.TntLibraryPlugin", parsed.get("main"));
        assertInstanceOf(String.class, parsed.get("api-version"),
                "api-version must be quoted; unquoted it parses as a double and 1.20 becomes 1.2");
        assertEquals("26.1", parsed.get("api-version"));
        assertNotNull(parsed.get("description"), "description is required");

        Object version = parsed.get("version");
        assertNotNull(version, "version is required");
        assertFalse(version.toString().contains("${"),
                "version still holds an unresolved Maven property: " + version);
    }

    @Test
    void pluginYmlDeclaresEveryCommandTheCodeLooksUp() throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> commands = (Map<String, Object>) parse(PLUGIN_YML).get("commands");
        assertNotNull(commands, "commands section is required");
        assertTrue(commands.containsKey("tntlibrary"),
                "the tntlibrary command must be declared or getCommand(\"tntlibrary\") returns null");
    }

    @Test
    void pluginYmlDeclaresEveryPermissionTheCodeChecks() throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> permissions = (Map<String, Object>) parse(PLUGIN_YML).get("permissions");
        assertNotNull(permissions, "permissions section is required");
        assertTrue(permissions.containsKey("tntlibrary.admin"), "tntlibrary.admin must be declared");

        // The command layer (TntCommand) gates give/list on this node and reload on the next.
        assertTrue(permissions.containsKey("tntlibrary.command.give"),
                "tntlibrary.command.give must be declared — TntCommand checks it for give/list");
        assertTrue(permissions.containsKey("tntlibrary.command.smart"),
                "tntlibrary.command.smart must be declared — TntCommand checks it for smart get/set");
        assertTrue(permissions.containsKey("tntlibrary.command.reload"),
                "tntlibrary.command.reload must be declared — TntCommand checks it for reload");

        // The listener layer (PlacementListener/IgnitionListener) gates placing and igniting the
        // Water Bomb on its per-bomb use node (Permissions.use("waterbomb")).
        assertTrue(permissions.containsKey("tntlibrary.use.waterbomb"),
                "tntlibrary.use.waterbomb must be declared — the place/ignite listeners check it");
    }

    @Test
    void pluginYmlDeclaresItsSoftDependencies() throws IOException {
        Object softdepend = parse(PLUGIN_YML).get("softdepend");
        assertNotNull(softdepend, "softdepend is required");
        String declared = softdepend.toString();
        assertTrue(declared.contains("WorldGuard"), "WorldGuard must be a soft dependency");
    }
}
