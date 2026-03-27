package com.classic.preservitory.server.content;

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

public final class NpcDefinitionLoader {

    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SHOPKEEPER_PATTERN = Pattern.compile("\"shopkeeper\"\\s*:\\s*(true|false)");
    private static final Pattern QUEST_PATTERN = Pattern.compile("\"questId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern REWARD_PATTERN = Pattern.compile("\"questRewardCoins\"\\s*:\\s*(\\d+)");
    private static final Pattern PRICE_ENTRY_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\\d+)");

    private NpcDefinitionLoader() {}

    public static Map<String, NpcDefinition> loadAll() {
        Map<String, NpcDefinition> defs = new LinkedHashMap<>();

        for (Path dir : candidateDirs()) {
            if (!Files.isDirectory(dir)) continue;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path file : stream) {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    NpcDefinition def = parse(json);
                    if (def != null) {
                        defs.put(def.id, def);
                    }
                }
                if (!defs.isEmpty()) {
                    return defs;
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load NPC definitions from " + dir, e);
            }
        }

        return defs;
    }

    private static NpcDefinition parse(String json) {
        String id = match(json, ID_PATTERN);
        String name = match(json, NAME_PATTERN);
        if (id == null || name == null) return null;

        boolean shopkeeper = "true".equals(match(json, SHOPKEEPER_PATTERN));
        String questId = match(json, QUEST_PATTERN);
        int rewardCoins = parseInt(match(json, REWARD_PATTERN), 0);

        return new NpcDefinition(
                id,
                name,
                shopkeeper,
                questId,
                rewardCoins,
                extractStringArray(json, "dialogueStart"),
                extractStringArray(json, "dialogueInProgress"),
                extractStringArray(json, "dialogueReadyToComplete"),
                extractStringArray(json, "dialogueComplete"),
                extractPriceMap(json, "stockPrices"),
                extractPriceMap(json, "sellPrices")
        );
    }

    private static List<String> extractStringArray(String json, String fieldName) {
        List<String> result = new ArrayList<>();
        String body = extractArrayBody(json, fieldName);
        if (body == null || body.isBlank()) return result;

        Matcher matcher = Pattern.compile("\"([^\"]*)\"").matcher(body);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private static LinkedHashMap<String, Integer> extractPriceMap(String json, String fieldName) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        String body = extractObjectBody(json, fieldName);
        if (body == null || body.isBlank()) return result;

        Matcher matcher = PRICE_ENTRY_PATTERN.matcher(body);
        while (matcher.find()) {
            result.put(matcher.group(1), Integer.parseInt(matcher.group(2)));
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
            if (c == ']' && --depth == 0) return json.substring(start + 1, i);
        }
        return null;
    }

    private static String extractObjectBody(String json, String fieldName) {
        int marker = json.indexOf("\"" + fieldName + "\"");
        if (marker == -1) return null;
        int start = json.indexOf('{', marker);
        if (start == -1) return null;
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return json.substring(start + 1, i);
        }
        return null;
    }

    private static String match(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static int parseInt(String value, int fallback) {
        return value != null ? Integer.parseInt(value) : fallback;
    }

    private static Path[] candidateDirs() {
        return new Path[]{
                Paths.get("cache", "npcs"),
                Paths.get("..", "Preservitory-Server", "cache", "npcs"),
                Paths.get("..", "Preservitory", "cache", "npcs")
        };
    }
}
