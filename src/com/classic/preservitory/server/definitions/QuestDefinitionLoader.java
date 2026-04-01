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
 * Reads every {@code *.json} from {@code cache/quests/} and returns a registry
 * of {@link QuestDefinition}s keyed by numeric ID.
 *
 * <p>Must be called AFTER {@link DialogueDefinitionManager} and
 * {@link ItemDefinitionManager} are loaded (validation cross-references both).
 *
 * <p>Fail-fast on:
 * <ul>
 *   <li>Duplicate quest ID
 *   <li>Unknown dialogue ID
 *   <li>Unknown item ID
 *   <li>Missing required fields
 * </ul>
 */
public final class QuestDefinitionLoader {

    private static final Pattern ID_PATTERN       = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern KEY_PATTERN      = Pattern.compile("\"key\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NAME_PATTERN     = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern REWARD_XP_PATTERN   = Pattern.compile("\"rewardXp\"\\s*:\\s*(\\d+)");
    private static final Pattern REWARD_SKILL_PATTERN = Pattern.compile("\"rewardXpSkill\"\\s*:\\s*\"([^\"]*)\"");

    private QuestDefinitionLoader() {}

    public static Map<Integer, QuestDefinition> loadAll() {
        Map<Integer, QuestDefinition> defs = new LinkedHashMap<>();

        for (Path dir : candidateDirs()) {
            if (!Files.isDirectory(dir)) continue;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path file : stream) {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    QuestDefinition def = parse(json, file);
                    if (defs.containsKey(def.id)) {
                        throw new IllegalStateException(
                                "[QuestDefinitionLoader] Duplicate quest id=" + def.id
                                + " in " + file);
                    }
                    defs.put(def.id, def);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load quest definitions from " + dir, e);
            }
            if (!defs.isEmpty()) {
                System.out.println("[QuestDefinitionLoader] Loaded " + defs.size()
                        + " quest definition(s) from " + dir);
                return defs;
            }
        }

        System.out.println("[QuestDefinitionLoader] No quest definitions found.");
        return defs;
    }

    // -----------------------------------------------------------------------
    //  Parsing
    // -----------------------------------------------------------------------

    private static QuestDefinition parse(String json, Path file) {
        String idStr = match(json, ID_PATTERN);
        String key   = match(json, KEY_PATTERN);
        String name  = match(json, NAME_PATTERN);

        if (idStr == null || key == null || name == null) {
            throw new IllegalStateException(
                    "[QuestDefinitionLoader] Missing 'id', 'key', or 'name' in " + file);
        }

        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "[QuestDefinitionLoader] Invalid numeric id in " + file);
        }

        List<String> startIds            = extractStringArray(json, "startDialogueIds");
        List<String> inProgressIds       = extractStringArray(json, "inProgressDialogueIds");
        List<String> readyToCompleteIds  = extractStringArray(json, "readyToCompleteDialogueIds");
        List<String> completedIds        = extractStringArray(json, "completedDialogueIds");

        // Validate every referenced dialogue ID exists.
        validateDialogueIds(startIds,           "startDialogueIds",           file);
        validateDialogueIds(inProgressIds,      "inProgressDialogueIds",      file);
        validateDialogueIds(readyToCompleteIds, "readyToCompleteDialogueIds", file);
        validateDialogueIds(completedIds,       "completedDialogueIds",       file);

        Map<Integer, Integer> requiredItems = extractIntMap(json, "requiredItems", file);
        Map<Integer, Integer> rewardItems   = extractIntMap(json, "rewardItems",   file);

        // Validate every referenced item ID exists.
        validateItemIds(requiredItems, "requiredItems", file);
        validateItemIds(rewardItems,   "rewardItems",   file);

        int    rewardXp    = parseInt(match(json, REWARD_XP_PATTERN), 0);
        String rewardSkill = match(json, REWARD_SKILL_PATTERN);

        List<QuestStage> stages = parseStages(json, file);

        return new QuestDefinition(id, key, name,
                startIds, inProgressIds, readyToCompleteIds, completedIds,
                requiredItems, rewardItems, rewardXp, rewardSkill, stages);
    }

    // -----------------------------------------------------------------------
    //  Stages parsing
    // -----------------------------------------------------------------------

    private static final Pattern STAGE_ID_PATTERN          = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern STAGE_DESC_PATTERN        = Pattern.compile("\"description\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern STAGE_DIALOGUE_ID_PATTERN = Pattern.compile("\"dialogueId\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern STAGE_ACTION_PATTERN      = Pattern.compile("\"action\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern OBJ_TYPE_PATTERN          = Pattern.compile("\"type\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern OBJ_ITEM_ID_PATTERN       = Pattern.compile("\"itemId\"\\s*:\\s*(\\d+)");
    private static final Pattern OBJ_AMOUNT_PATTERN        = Pattern.compile("\"amount\"\\s*:\\s*(\\d+)");
    private static final Pattern OBJ_TARGET_ID_PATTERN     = Pattern.compile("\"targetId\"\\s*:\\s*(\\d+)");

    private static List<QuestStage> parseStages(String json, Path file) {
        List<QuestStage> stages = new ArrayList<>();
        String arrayBody = extractArrayBody(json, "stages");
        if (arrayBody == null || arrayBody.isBlank()) return stages;

        for (String stageBody : splitObjectBodies(arrayBody)) {
            stages.add(parseStage(stageBody, file));
        }

        // Validate stage dialogue IDs
        for (QuestStage stage : stages) {
            if (stage.dialogueId != null) {
                if (DialogueDefinitionManager.get(stage.dialogueId) == null) {
                    throw new IllegalStateException(
                            "[QuestDefinitionLoader] Unknown dialogue id '" + stage.dialogueId
                            + "' in stage " + stage.id + " in " + file);
                }
            }
        }
        return stages;
    }

    private static QuestStage parseStage(String stageBody, Path file) {
        String idStr = match(stageBody, STAGE_ID_PATTERN);
        if (idStr == null) {
            throw new IllegalStateException(
                    "[QuestDefinitionLoader] Stage missing 'id' in " + file);
        }
        int    id         = Integer.parseInt(idStr);
        String desc       = match(stageBody, STAGE_DESC_PATTERN);
        String dialogueId = match(stageBody, STAGE_DIALOGUE_ID_PATTERN);
        String action     = match(stageBody, STAGE_ACTION_PATTERN);

        QuestObjective objective = parseStageObjective(stageBody, file);
        return new QuestStage(id, desc, dialogueId, objective, action);
    }

    private static QuestObjective parseStageObjective(String stageBody, Path file) {
        int marker = stageBody.indexOf("\"objective\"");
        if (marker == -1) return null;

        // Check if value is JSON null
        int colon = stageBody.indexOf(':', marker);
        if (colon == -1) return null;
        int valueStart = colon + 1;
        while (valueStart < stageBody.length()
                && Character.isWhitespace(stageBody.charAt(valueStart))) valueStart++;
        if (valueStart < stageBody.length()
                && stageBody.regionMatches(valueStart, "null", 0, 4)) return null;

        // Extract objective object body
        int start = stageBody.indexOf('{', colon);
        if (start == -1) return null;
        int depth = 0;
        int end = start;
        for (int i = start; i < stageBody.length(); i++) {
            char c = stageBody.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) { end = i; break; }
        }
        String objBody = stageBody.substring(start + 1, end);

        String typeStr = match(objBody, OBJ_TYPE_PATTERN);
        if (typeStr == null) {
            throw new IllegalStateException(
                    "[QuestDefinitionLoader] Objective missing 'type' in " + file);
        }
        QuestObjective.Type type;
        try {
            type = QuestObjective.Type.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "[QuestDefinitionLoader] Unknown objective type '" + typeStr + "' in " + file);
        }

        int itemId   = parseInt(match(objBody, OBJ_ITEM_ID_PATTERN), 0);
        int amount   = parseInt(match(objBody, OBJ_AMOUNT_PATTERN), 1);
        int targetId = parseInt(match(objBody, OBJ_TARGET_ID_PATTERN), 0);

        if (type == QuestObjective.Type.GATHER && itemId != 0
                && !ItemDefinitionManager.exists(itemId)) {
            throw new IllegalStateException(
                    "[QuestDefinitionLoader] Unknown item id=" + itemId
                    + " in stage objective in " + file);
        }
        return new QuestObjective(type, itemId, amount, targetId);
    }

    /**
     * Splits the content of a JSON array into a list of raw object bodies
     * (the content between each matching {@code {'{}'}} and {@code {'}'} pair).
     */
    private static List<String> splitObjectBodies(String arrayBody) {
        List<String> result = new ArrayList<>();
        int i = 0;
        int len = arrayBody.length();
        while (i < len) {
            while (i < len && arrayBody.charAt(i) != '{') i++;
            if (i >= len) break;
            int start = i;
            int depth = 0;
            while (i < len) {
                char c = arrayBody.charAt(i++);
                if      (c == '{')             depth++;
                else if (c == '}' && --depth == 0) {
                    result.add(arrayBody.substring(start + 1, i - 1));
                    break;
                }
            }
        }
        return result;
    }

    // -----------------------------------------------------------------------
    //  Validation helpers
    // -----------------------------------------------------------------------

    private static void validateDialogueIds(List<String> ids, String fieldName, Path file) {
        for (String did : ids) {
            if (DialogueDefinitionManager.get(did) == null) {
                throw new IllegalStateException(
                        "[QuestDefinitionLoader] Unknown dialogue id '" + did
                        + "' in field '" + fieldName + "' in " + file);
            }
        }
    }

    private static void validateItemIds(Map<Integer, Integer> items, String fieldName, Path file) {
        for (int itemId : items.keySet()) {
            if (!ItemDefinitionManager.exists(itemId)) {
                throw new IllegalStateException(
                        "[QuestDefinitionLoader] Unknown item id=" + itemId
                        + " in field '" + fieldName + "' in " + file);
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Parsing utilities
    // -----------------------------------------------------------------------

    /** Extracts a JSON string array: {@code "fieldName": ["a", "b"]}. */
    private static List<String> extractStringArray(String json, String fieldName) {
        List<String> result = new ArrayList<>();
        String body = extractArrayBody(json, fieldName);
        if (body == null || body.isBlank()) return result;
        Matcher m = Pattern.compile("\"([^\"]*)\"").matcher(body);
        while (m.find()) result.add(m.group(1));
        return result;
    }

    /**
     * Extracts a JSON object mapping string keys to integer values:
     * {@code "fieldName": { "2": 3, "1": 50 }}.
     */
    private static Map<Integer, Integer> extractIntMap(String json, String fieldName, Path file) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        String body = extractObjectBody(json, fieldName);
        if (body == null || body.isBlank()) return result;

        Matcher m = Pattern.compile("\"(\\d+)\"\\s*:\\s*(\\d+)").matcher(body);
        while (m.find()) {
            try {
                result.put(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
            } catch (NumberFormatException e) {
                throw new IllegalStateException(
                        "[QuestDefinitionLoader] Non-numeric entry in '" + fieldName + "' in " + file);
            }
        }
        return result;
    }

    /** Returns the content between the first {@code [} and matching {@code ]} for {@code fieldName}. */
    private static String extractArrayBody(String json, String fieldName) {
        int marker = json.indexOf("\"" + fieldName + "\"");
        if (marker == -1) return null;
        int start = json.indexOf('[', marker);
        if (start == -1) return null;
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if      (c == '[') depth++;
            else if (c == ']' && --depth == 0) return json.substring(start + 1, i);
        }
        return null;
    }

    /** Returns the content between the first {@code {'{}'} and matching {@code {'}'}} for {@code fieldName}. */
    private static String extractObjectBody(String json, String fieldName) {
        int marker = json.indexOf("\"" + fieldName + "\"");
        if (marker == -1) return null;
        int start = json.indexOf('{', marker);
        if (start == -1) return null;
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if      (c == '{') depth++;
            else if (c == '}' && --depth == 0) return json.substring(start + 1, i);
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

    private static Path[] candidateDirs() {
        return new Path[]{
            Paths.get("cache", "quests"),
            Paths.get("..", "Preservitory-Server", "cache", "quests"),
            Paths.get("..", "Preservitory",        "cache", "quests")
        };
    }
}
