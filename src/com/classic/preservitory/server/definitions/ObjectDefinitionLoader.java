package com.classic.preservitory.server.definitions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads every {@code *.json} from {@code cache/objects/} and returns a
 * registry of {@link ObjectDefinition}s keyed by numeric ID.
 */
public final class ObjectDefinitionLoader {

    private static final Pattern ID_PATTERN        = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern NAME_PATTERN      = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TYPE_PATTERN      = Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern RESOURCE_PATTERN  = Pattern.compile("\"resourceItemId\"\\s*:\\s*(\\d+)");
    private static final Pattern XP_PATTERN        = Pattern.compile("\"xp\"\\s*:\\s*(\\d+)");
    private static final Pattern RESPAWN_PATTERN   = Pattern.compile("\"respawnMs\"\\s*:\\s*(\\d+)");
    private static final Pattern SPRITE_KEY_PATTERN = Pattern.compile("\"spriteKey\"\\s*:\\s*\"([^\"]+)\"");

    private ObjectDefinitionLoader() {}

    public static Map<Integer, ObjectDefinition> loadAll() {
        Map<Integer, ObjectDefinition> defs = new LinkedHashMap<>();

        for (Path dir : candidateDirs()) {
            if (!Files.isDirectory(dir)) continue;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path file : stream) {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    String key  = fileKey(file);
                    ObjectDefinition def = parse(json, key);
                    if (def != null) {
                        defs.put(def.id, def);
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load object definitions from " + dir, e);
            }
            if (!defs.isEmpty()) return defs;
        }

        System.out.println("[ObjectDefinitionLoader] No object definitions found.");
        return defs;
    }

    // -----------------------------------------------------------------------
    //  Parsing
    // -----------------------------------------------------------------------

    private static ObjectDefinition parse(String json, String key) {
        String idStr   = match(json, ID_PATTERN);
        String typeStr = match(json, TYPE_PATTERN);
        if (idStr == null || typeStr == null) return null;

        try {
            int id             = Integer.parseInt(idStr);
            String name        = firstNonNull(match(json, NAME_PATTERN), key);
            ObjectDefinition.Type type = ObjectDefinition.Type.valueOf(typeStr.toUpperCase());
            int resourceItemId = parseInt(match(json, RESOURCE_PATTERN), 0);
            int xp             = parseInt(match(json, XP_PATTERN), 0);
            long respawnMs     = parseLong(match(json, RESPAWN_PATTERN), 10_000L);
            String spriteKey   = firstNonNull(match(json, SPRITE_KEY_PATTERN), key);

            return new ObjectDefinition(id, key, name, type, resourceItemId, xp, respawnMs, spriteKey);
        } catch (IllegalArgumentException e) {
            System.err.println("[ObjectDefinitionLoader] Skipping " + key + ": " + e.getMessage());
            return null;
        }
    }

    private static String match(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static int parseInt(String value, int fallback) {
        return value != null ? Integer.parseInt(value) : fallback;
    }

    private static long parseLong(String value, long fallback) {
        return value != null ? Long.parseLong(value) : fallback;
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    private static String fileKey(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }

    private static Path[] candidateDirs() {
        return new Path[]{
            Paths.get("cache", "objects"),
            Paths.get("..", "Preservitory-Server", "cache", "objects"),
            Paths.get("..", "Preservitory",        "cache", "objects")
        };
    }
}
