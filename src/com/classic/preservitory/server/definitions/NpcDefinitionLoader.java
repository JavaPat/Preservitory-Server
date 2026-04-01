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
 * Reads every {@code *.json} from {@code cache/npcs/} and returns a
 * registry of {@link NpcDefinition}s keyed by numeric ID.
 */
public final class NpcDefinitionLoader {

    private static final Pattern ID_PATTERN            = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern NAME_PATTERN          = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SHOPKEEPER_PATTERN    = Pattern.compile("\"shopkeeper\"\\s*:\\s*(true|false)");
    private static final Pattern SHOP_ID_PATTERN       = Pattern.compile("\"shopId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern QUEST_PATTERN         = Pattern.compile("\"questId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern WANDER_PATTERN        = Pattern.compile("\"wander\"\\s*:\\s*(true|false)");
    private static final Pattern WANDER_RADIUS_PATTERN = Pattern.compile("\"wanderRadius\"\\s*:\\s*(\\d+)");

    private NpcDefinitionLoader() {}

    public static Map<Integer, NpcDefinition> loadAll() {
        Map<Integer, NpcDefinition> defs = new LinkedHashMap<>();

        for (Path dir : candidateDirs()) {
            if (!Files.isDirectory(dir)) continue;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path file : stream) {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    String key  = fileKey(file);
                    NpcDefinition def = parse(json, key);
                    if (def != null) {
                        defs.put(def.id, def);
                    }
                }
                if (!defs.isEmpty()) return defs;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load NPC definitions from " + dir, e);
            }
        }

        System.out.println("[NpcDefinitionLoader] No NPC definitions found.");
        return defs;
    }

    // -----------------------------------------------------------------------
    //  Parsing
    // -----------------------------------------------------------------------

    private static NpcDefinition parse(String json, String key) {
        String idStr = match(json, ID_PATTERN);
        String name  = match(json, NAME_PATTERN);
        if (idStr == null || name == null) return null;

        try {
            int     id          = Integer.parseInt(idStr);
            boolean shopkeeper        = "true".equals(match(json, SHOPKEEPER_PATTERN));
            String  shopId            = match(json, SHOP_ID_PATTERN);
            String  questId           = match(json, QUEST_PATTERN);
            boolean wander            = "true".equals(match(json, WANDER_PATTERN));
            int     wanderRadiusTiles = parseInt(match(json, WANDER_RADIUS_PATTERN), 0);

            return new NpcDefinition(
                    id, key, name, shopkeeper, shopId, questId,
                    wander, wanderRadiusTiles
            );
        } catch (NumberFormatException e) {
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

    private static String fileKey(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }

    private static Path[] candidateDirs() {
        return new Path[]{
            Paths.get("cache", "npcs"),
            Paths.get("..", "Preservitory-Server", "cache", "npcs"),
            Paths.get("..", "Preservitory",        "cache", "npcs")
        };
    }
}
