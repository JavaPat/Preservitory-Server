package com.classic.preservitory.server.spawns;

import com.classic.preservitory.server.definitions.EnemyDefinitionManager;
import com.classic.preservitory.server.definitions.NpcDefinitionManager;
import com.classic.preservitory.server.definitions.ObjectDefinitionManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads spawn data from JSON files in {@code cache/spawns/}.
 *
 * <p>Each entry must have four fields:
 * <pre>
 *   { "id": "goblin_1", "definitionId": 1, "x": 256, "y": 192 }
 * </pre>
 *
 * Validation at load time:
 * <ul>
 *   <li>{@code id} is present and non-empty</li>
 *   <li>No duplicate {@code id} values within the same file</li>
 *   <li>{@code definitionId} exists in the relevant definition manager</li>
 * </ul>
 */
public final class SpawnLoader {

    private static final String SPAWNS_DIR = "spawns";

    private static final Pattern ENTRY_PATTERN  = Pattern.compile("\\{([^{}]*)\\}");
    private static final Pattern ID_PATTERN     = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DEF_ID_PATTERN = Pattern.compile("\"definitionId\"\\s*:\\s*(\\d+)");
    private static final Pattern X_PATTERN      = Pattern.compile("\"x\"\\s*:\\s*(-?\\d+)");
    private static final Pattern Y_PATTERN      = Pattern.compile("\"y\"\\s*:\\s*(-?\\d+)");

    private SpawnLoader() {}

    public static List<SpawnEntry> loadNpcSpawns() {
        return load("npcs.json", "NPC", defId -> {
            if (!NpcDefinitionManager.exists(defId))
                throw new IllegalStateException(
                        "[SpawnLoader] NPC spawn references unknown definitionId=" + defId
                        + ". Check cache/npcs/.");
        });
    }

    public static List<SpawnEntry> loadEnemySpawns() {
        return load("enemies.json", "Enemy", defId -> {
            if (!EnemyDefinitionManager.exists(defId))
                throw new IllegalStateException(
                        "[SpawnLoader] Enemy spawn references unknown definitionId=" + defId
                        + ". Check cache/enemies/.");
        });
    }

    public static List<SpawnEntry> loadObjectSpawns() {
        return load("objects.json", "Object", defId -> {
            if (!ObjectDefinitionManager.exists(defId))
                throw new IllegalStateException(
                        "[SpawnLoader] Object spawn references unknown definitionId=" + defId
                        + ". Check cache/objects/.");
        });
    }

    // -----------------------------------------------------------------------
    //  Internals
    // -----------------------------------------------------------------------

    private static List<SpawnEntry> load(String filename, String label, DefIdValidator defValidator) {
        Path path = resolvePath(filename);
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            List<SpawnEntry> result = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();

            Matcher m = ENTRY_PATTERN.matcher(json);
            while (m.find()) {
                String entry = m.group(1);

                String spawnId = extractString(entry, ID_PATTERN, "id");
                if (spawnId.isEmpty()) {
                    throw new IllegalStateException(
                            "[SpawnLoader] Spawn entry in " + filename + " has an empty 'id' field: {" + entry + "}");
                }
                if (!seenIds.add(spawnId)) {
                    throw new IllegalStateException(
                            "[SpawnLoader] Duplicate spawn id '" + spawnId + "' in " + filename);
                }

                int defId = extractInt(entry, DEF_ID_PATTERN, "definitionId");
                int x     = extractInt(entry, X_PATTERN, "x");
                int y     = extractInt(entry, Y_PATTERN, "y");

                defValidator.validate(defId);
                result.add(new SpawnEntry(spawnId, defId, x, y));
            }

            System.out.println("[SpawnLoader] Loaded " + result.size() + " " + label + " spawns from " + filename + ".");
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read spawn file: " + path, e);
        }
    }

    private static Path resolvePath(String filename) {
        List<Path> candidates = List.of(
                Paths.get("cache", SPAWNS_DIR, filename),
                Paths.get("..", "Preservitory-Server", "cache", SPAWNS_DIR, filename)
        );
        for (Path p : candidates) {
            if (Files.exists(p)) return p;
        }
        throw new IllegalStateException(
                "Spawn file not found: " + filename
                + ". Expected at cache/spawns/" + filename);
    }

    private static String extractString(String entry, Pattern pattern, String field) {
        Matcher m = pattern.matcher(entry);
        if (!m.find()) {
            throw new IllegalStateException(
                    "Missing field '" + field + "' in spawn entry: {" + entry + "}");
        }
        return m.group(1);
    }

    private static int extractInt(String entry, Pattern pattern, String field) {
        Matcher m = pattern.matcher(entry);
        if (!m.find()) {
            throw new IllegalStateException(
                    "Missing field '" + field + "' in spawn entry: {" + entry + "}");
        }
        return Integer.parseInt(m.group(1));
    }

    @FunctionalInterface
    private interface DefIdValidator {
        void validate(int defId);
    }
}
