package com.classic.preservitory.server.content;

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

public final class ObjectTypeLoader {

    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("\"category\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern RESPAWN_PATTERN = Pattern.compile("\"respawnMs\"\\s*:\\s*(\\d+)");

    private ObjectTypeLoader() {}

    public static Map<String, ObjectTypeDefinition> loadAll() {
        Map<String, ObjectTypeDefinition> defs = new LinkedHashMap<>();

        for (Path dir : candidateDirs()) {
            if (!Files.isDirectory(dir)) continue;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path file : stream) {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    ObjectTypeDefinition def = parse(json);
                    if (def != null) {
                        defs.put(def.id, def);
                    }
                }
                if (!defs.isEmpty()) {
                    return defs;
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load object definitions from " + dir, e);
            }
        }

        return defs;
    }

    private static ObjectTypeDefinition parse(String json) {
        String id = match(json, ID_PATTERN);
        String category = match(json, CATEGORY_PATTERN);
        String respawn = match(json, RESPAWN_PATTERN);
        if (id == null || category == null) return null;

        long respawnMs = respawn != null ? Long.parseLong(respawn) : 0L;
        return new ObjectTypeDefinition(id, category, respawnMs);
    }

    private static String match(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Path[] candidateDirs() {
        return new Path[]{
                Paths.get("cache", "objects"),
                Paths.get("..", "Preservitory", "cache", "objects")
        };
    }
}
