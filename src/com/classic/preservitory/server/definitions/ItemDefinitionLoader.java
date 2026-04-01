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
 * Reads every {@code *.json} file from {@code cache/items/} and returns a
 * registry of {@link ItemDefinition} objects keyed by item ID.
 *
 * Uses the same lightweight regex-based micro-parser as the NPC and shop loaders;
 * no external JSON library required.
 */
public final class ItemDefinitionLoader {

    private static final Pattern ID_PATTERN           = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern NAME_PATTERN         = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern VALUE_PATTERN        = Pattern.compile("\"value\"\\s*:\\s*(\\d+)");
    private static final Pattern STACKABLE_PATTERN    = Pattern.compile("\"stackable\"\\s*:\\s*(true|false)");
    private static final Pattern TRADABLE_PATTERN     = Pattern.compile("\"tradable\"\\s*:\\s*(true|false)");
    private static final Pattern EQUIP_SLOT_PATTERN   = Pattern.compile("\"equipSlot\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ATTACK_BONUS_PATTERN = Pattern.compile("\"attackBonus\"\\s*:\\s*(-?\\d+)");
    private static final Pattern STR_BONUS_PATTERN    = Pattern.compile("\"strengthBonus\"\\s*:\\s*(-?\\d+)");

    private ItemDefinitionLoader() {}

    /**
     * Load all item definitions and return them as a {@code Map<id, ItemDefinition>}.
     * Stops at the first {@code cache/items} directory that exists.
     */
    public static Map<Integer, ItemDefinition> loadAll() {
        Map<Integer, ItemDefinition> defs = new LinkedHashMap<>();

        for (Path dir : candidateDirs()) {
            if (!Files.isDirectory(dir)) continue;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path file : stream) {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    ItemDefinition def = parse(json);
                    if (def != null) {
                        defs.put(def.id, def);
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load item definitions from " + dir, e);
            }
            if (!defs.isEmpty()) return defs;
        }

        System.out.println("[ItemDefinitionLoader] No item definitions found.");
        return defs;
    }

    // -----------------------------------------------------------------------
    //  Parsing
    // -----------------------------------------------------------------------

    private static ItemDefinition parse(String json) {
        String idStr = match(json, ID_PATTERN);
        String name  = match(json, NAME_PATTERN);
        if (idStr == null || name == null) return null;

        try {
            int     id            = Integer.parseInt(idStr);
            int     value         = parseInt(match(json, VALUE_PATTERN), 0);
            boolean stackable     = "true".equals(match(json, STACKABLE_PATTERN));
            boolean tradable      = "true".equals(match(json, TRADABLE_PATTERN));
            String  equipSlot     = match(json, EQUIP_SLOT_PATTERN);   // null if absent
            int     attackBonus   = parseInt(match(json, ATTACK_BONUS_PATTERN), 0);
            int     strengthBonus = parseInt(match(json, STR_BONUS_PATTERN), 0);
            return new ItemDefinition(id, name, value, stackable, tradable,
                                      equipSlot, attackBonus, strengthBonus);
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

    private static Path[] candidateDirs() {
        return new Path[]{
            Paths.get("cache", "items"),
            Paths.get("..", "Preservitory-Server", "cache", "items"),
            Paths.get("..", "Preservitory",        "cache", "items")
        };
    }
}
