package com.classic.preservitory.server.definitions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads every {@code *.json} from {@code cache/enemies/} and returns a
 * registry of {@link EnemyDefinition}s keyed by numeric ID.
 */
public final class EnemyDefinitionLoader {

    private static final Pattern ID_PATTERN       = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern NAME_PATTERN      = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MAX_HP_PATTERN    = Pattern.compile("\"maxHp\"\\s*:\\s*(\\d+)");
    private static final Pattern ATTACK_PATTERN    = Pattern.compile("\"attack\"\\s*:\\s*(\\d+)");
    private static final Pattern DEFENSE_PATTERN   = Pattern.compile("\"defense\"\\s*:\\s*(\\d+)");
    private static final Pattern MIN_DMG_PATTERN   = Pattern.compile("\"minDamage\"\\s*:\\s*(\\d+)");
    private static final Pattern MAX_DMG_PATTERN   = Pattern.compile("\"maxDamage\"\\s*:\\s*(\\d+)");
    private static final Pattern COOLDOWN_PATTERN   = Pattern.compile("\"attackCooldownMs\"\\s*:\\s*(\\d+)");
    private static final Pattern RESPAWN_PATTERN    = Pattern.compile("\"respawnDelayMs\"\\s*:\\s*(\\d+)");
    private static final Pattern WANDER_PATTERN        = Pattern.compile("\"wander\"\\s*:\\s*(true|false)");
    private static final Pattern WANDER_RADIUS_PATTERN = Pattern.compile("\"wanderRadius\"\\s*:\\s*(\\d+)");

    // Drop table entry patterns
    private static final Pattern DROP_ITEM_PATTERN   = Pattern.compile("\"itemId\"\\s*:\\s*(\\d+)");
    private static final Pattern DROP_CHANCE_PATTERN = Pattern.compile("\"chance\"\\s*:\\s*([\\d.]+)");
    private static final Pattern DROP_MIN_PATTERN    = Pattern.compile("\"minAmount\"\\s*:\\s*(\\d+)");
    private static final Pattern DROP_MAX_PATTERN    = Pattern.compile("\"maxAmount\"\\s*:\\s*(\\d+)");

    private EnemyDefinitionLoader() {}

    public static Map<Integer, EnemyDefinition> loadAll() {
        Map<Integer, EnemyDefinition> defs = new LinkedHashMap<>();

        for (Path dir : candidateDirs()) {
            if (!Files.isDirectory(dir)) continue;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path file : stream) {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    String key  = fileKey(file);
                    EnemyDefinition def = parse(json, key);
                    if (def != null) {
                        defs.put(def.id, def);
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load enemy definitions from " + dir, e);
            }
            if (!defs.isEmpty()) return defs;
        }

        System.out.println("[EnemyDefinitionLoader] No enemy definitions found.");
        return defs;
    }

    // -----------------------------------------------------------------------
    //  Parsing
    // -----------------------------------------------------------------------

    private static EnemyDefinition parse(String json, String key) {
        String idStr   = match(json, ID_PATTERN);
        String name    = match(json, NAME_PATTERN);
        if (idStr == null || name == null) return null;

        try {
            int  id               = Integer.parseInt(idStr);
            int  maxHp            = parseInt(match(json, MAX_HP_PATTERN), 10);
            int  attack           = parseInt(match(json, ATTACK_PATTERN), 1);
            int  defense          = parseInt(match(json, DEFENSE_PATTERN), 1);
            int  minDamage        = parseInt(match(json, MIN_DMG_PATTERN), 1);
            int  maxDamage        = parseInt(match(json, MAX_DMG_PATTERN), 3);
            long    cooldownMs        = parseLong(match(json, COOLDOWN_PATTERN), 2000L);
            long    respawnDelayMs    = parseLong(match(json, RESPAWN_PATTERN), 30_000L);
            boolean wander            = "true".equals(match(json, WANDER_PATTERN));
            int     wanderRadiusTiles = parseInt(match(json, WANDER_RADIUS_PATTERN), 0);
            List<EnemyDefinition.DropEntry> drops = parseDropTable(json);

            return new EnemyDefinition(id, key, name, maxHp, attack, defense,
                                       minDamage, maxDamage, cooldownMs, respawnDelayMs,
                                       drops, wander, wanderRadiusTiles);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<EnemyDefinition.DropEntry> parseDropTable(String json) {
        List<EnemyDefinition.DropEntry> result = new ArrayList<>();
        String body = extractArrayBody(json, "dropTable");
        if (body == null || body.isBlank()) return result;

        // Split on '}' boundaries to get individual drop objects
        Matcher objMatcher = Pattern.compile("\\{([^}]*)\\}").matcher(body);
        while (objMatcher.find()) {
            String entry = objMatcher.group(1);
            String itemStr   = match(entry, DROP_ITEM_PATTERN);
            String chanceStr = match(entry, DROP_CHANCE_PATTERN);
            if (itemStr != null && chanceStr != null) {
                int minAmt = parseInt(match(entry, DROP_MIN_PATTERN), 1);
                int maxAmt = parseInt(match(entry, DROP_MAX_PATTERN), minAmt);
                result.add(new EnemyDefinition.DropEntry(
                        Integer.parseInt(itemStr),
                        Double.parseDouble(chanceStr),
                        minAmt, maxAmt));
            }
        }
        return result;
    }

    private static String extractArrayBody(String json, String fieldName) {
        int marker = json.indexOf("\"" + fieldName + "\"");
        if (marker == -1) return null;
        int start = json.indexOf('[', marker);
        if (start == -1) return null;
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            else if (c == ']' && --depth == 0) return json.substring(start + 1, i);
        }
        return null;
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

    private static String fileKey(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }

    private static Path[] candidateDirs() {
        return new Path[]{
            Paths.get("cache", "enemies"),
            Paths.get("..", "Preservitory-Server", "cache", "enemies"),
            Paths.get("..", "Preservitory",        "cache", "enemies")
        };
    }
}
