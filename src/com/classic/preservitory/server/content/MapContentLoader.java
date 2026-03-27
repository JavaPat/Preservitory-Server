package com.classic.preservitory.server.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MapContentLoader {

    private static final String MAP_NAME = "starter_map.json";
    private static final Pattern OBJECT_PATTERN = Pattern.compile("\\{([^{}]*)\\}");
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DEFINITION_PATTERN = Pattern.compile("\"definition\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern X_PATTERN = Pattern.compile("\"x\"\\s*:\\s*(-?\\d+)");
    private static final Pattern Y_PATTERN = Pattern.compile("\"y\"\\s*:\\s*(-?\\d+)");

    private MapContentLoader() {}

    public static List<MapObjectSpawn> loadObjects() {
        List<MapSpawn> raw = loadArray("objects");
        List<MapObjectSpawn> result = new ArrayList<>();
        for (MapSpawn spawn : raw) {
            result.add(new MapObjectSpawn(spawn.id, spawn.definitionId, spawn.x, spawn.y));
        }
        return result;
    }

    public static List<MapNpcSpawn> loadNpcs() {
        List<MapSpawn> raw = loadArray("npcs");
        List<MapNpcSpawn> result = new ArrayList<>();
        for (MapSpawn spawn : raw) {
            result.add(new MapNpcSpawn(spawn.id, spawn.definitionId, spawn.x, spawn.y));
        }
        return result;
    }

    private static List<MapSpawn> loadArray(String arrayName) {
        Path mapPath = resolveMapPath();
        try {
            String json = Files.readString(mapPath, StandardCharsets.UTF_8);
            String body = extractNamedArray(json, arrayName);
            List<MapSpawn> result = new ArrayList<>();
            if (body == null) {
                return result;
            }

            Matcher matcher = OBJECT_PATTERN.matcher(body);
            while (matcher.find()) {
                String object = matcher.group(1);
                String id = extractString(object, ID_PATTERN);
                if (id == null || id.isBlank()) {
                    continue;
                }

                String definitionId = extractString(object, DEFINITION_PATTERN);
                if (definitionId == null || definitionId.isBlank()) {
                    definitionId = id;
                }

                result.add(new MapSpawn(
                        id,
                        definitionId,
                        extractInt(object, X_PATTERN),
                        extractInt(object, Y_PATTERN)
                ));
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load map content from " + mapPath, e);
        }
    }

    private static Path resolveMapPath() {
        List<Path> candidates = List.of(
                Paths.get("cache", "maps", MAP_NAME),
                Paths.get("..", "Preservitory-Server", "cache", "maps", MAP_NAME),
                Paths.get("..", "Preservitory", "cache", "maps", MAP_NAME)
        );
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find map file " + MAP_NAME);
    }

    private static String extractNamedArray(String json, String arrayName) {
        int marker = json.indexOf("\"" + arrayName + "\"");
        if (marker == -1) return null;
        int start = json.indexOf('[', marker);
        if (start == -1) return null;
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            if (c == ']' && --depth == 0) return json.substring(start + 1, i);
        }
        return null;
    }

    private static String extractString(String object, Pattern pattern) {
        Matcher matcher = pattern.matcher(object);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static int extractInt(String object, Pattern pattern) {
        Matcher matcher = pattern.matcher(object);
        if (!matcher.find()) {
            throw new IllegalStateException("Missing numeric field in map object: {" + object + "}");
        }
        return Integer.parseInt(matcher.group(1));
    }

    private record MapSpawn(String id, String definitionId, int x, int y) {}

    public record MapObjectSpawn(String id, String definitionId, int x, int y) {}

    public record MapNpcSpawn(String id, String definitionId, int x, int y) {}
}
