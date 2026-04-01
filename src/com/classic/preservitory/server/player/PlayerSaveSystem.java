package com.classic.preservitory.server.player;

import com.classic.preservitory.server.moderation.PlayerRole;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayerSaveSystem {

    private static final Path SAVE_DIR = resolveSaveDir();
    private static final Pattern STRING_FIELD_PATTERN =
            Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern INT_FIELD_PATTERN =
            Pattern.compile("\"%s\"\\s*:\\s*(-?\\d+)");

    // -----------------------------------------------------------------------
    //  Save
    // -----------------------------------------------------------------------

    public static void save(PlayerData data) {
        if (data == null || data.username == null) return;

        Path file = jsonFile(normalizeUsername(data.username));
        try {
            Files.writeString(file, serialize(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("[SaveSystem] Failed to save player: " + data.username);
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------------------
    //  Load
    // -----------------------------------------------------------------------

    public static PlayerData load(String username) {
        if (username == null || username.isBlank()) return null;

        String normalized = normalizeUsername(username);
        Path jsonFile = jsonFile(normalized);
        if (Files.exists(jsonFile)) {
            try {
                return deserialize(Files.readString(jsonFile, StandardCharsets.UTF_8));
            } catch (Exception e) {
                System.out.println("[SaveSystem] Failed to load player JSON: " + username);
                e.printStackTrace();
                return null;
            }
        }

        File datFile = datFile(normalized).toFile();
        if (!datFile.exists()) return null;

        try (ObjectInputStream in = new ObjectInputStream(
                new FileInputStream(datFile))) {

            PlayerData data = (PlayerData) in.readObject();
            if (data != null) {
                save(data);
            }
            return data;

        } catch (Exception e) {
            System.out.println("[SaveSystem] Failed to load player: " + username);
            return null; // ⚠️ prevents crash
        }
    }

    // -----------------------------------------------------------------------
    //  Exists
    // -----------------------------------------------------------------------

    public static boolean exists(String username) {
        if (username == null || username.isBlank()) return false;
        String normalized = normalizeUsername(username);
        return Files.exists(jsonFile(normalized)) || Files.exists(datFile(normalized));
    }

    private static Path resolveSaveDir() {
        Path dir = Paths.get(
                "C:\\Users\\Patri\\IdeaProjects\\Preservitory-Server\\saves\\player");
        try {
            Files.createDirectories(dir);
            System.out.println("[SaveSystem] Using save directory: " + dir.toAbsolutePath());
            return dir;
        } catch (Exception e) {
            throw new IllegalStateException("Could not create player save directory: " + dir, e);
        }
    }

    private static String normalizeUsername(String username) {
        return username.trim().toLowerCase();
    }

    private static Path jsonFile(String normalizedUsername) {
        return SAVE_DIR.resolve(normalizedUsername + ".json");
    }

    private static Path datFile(String normalizedUsername) {
        return SAVE_DIR.resolve(normalizedUsername + ".dat");
    }

    private static String serialize(PlayerData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendStringField(sb, "username", data.username, true);
        if (data.passwordHash == null || data.passwordHash.isBlank()) {
            System.out.println("[SaveSystem] WARNING: Missing password hash for " + data.username);
            throw new IllegalStateException("Password hash missing for user: " + data.username);
        }
        appendStringField(sb, "passwordHash", data.passwordHash, true);
        appendStringField(sb, "rights", data.rights != null ? data.rights.name() : PlayerRole.PLAYER.name(), true);
        appendIntField(sb, "x", data.x, true);
        appendIntField(sb, "y", data.y, true);
        appendIntField(sb, "hp", data.hp, true);
        appendStringField(sb, "questState", data.questState, true);
        appendIntField(sb, "questLogsChopped", data.questLogsChopped, true);
        appendStringMapField(sb, "quests", data.quests, true);
        appendMapField(sb, "inventory",  data.inventory,  true);
        appendMapField(sb, "skills",     data.skills,     true);
        appendMapField(sb, "skillXp",    data.skillXp,    true);
        appendMapField(sb, "equipment",  data.equipment,  false);
        sb.append("}\n");
        return sb.toString();
    }

    private static PlayerData deserialize(String json) {
        String username = extractStringField(json, "username");
        if (username == null || username.isBlank()) {
            return null;
        }

        PlayerData data = new PlayerData(username);
        data.passwordHash = extractStringField(json, "passwordHash");

        data.rights = PlayerRole.PLAYER; // default first

        String rightsStr = extractStringField(json, "rights");
        if (rightsStr != null) {
            try {
                data.rights = PlayerRole.valueOf(rightsStr);
            } catch (IllegalArgumentException ignored) {
                System.out.println("[PlayerSaveSystem] Invalid role: " + rightsStr + ", for player: " + username);
            }
        }

        data.x = extractIntField(json, "x", PlayerSession.PLAYER_SPAWN_X);
        data.y = extractIntField(json, "y", PlayerSession.PLAYER_SPAWN_Y);
        data.hp = extractIntField(json, "hp", 5);
        data.questState = defaultString(extractStringField(json, "questState"), "NOT_STARTED");
        data.questLogsChopped = extractIntField(json, "questLogsChopped", 0);
        data.quests.putAll(extractStringMapField(json, "quests"));
        data.inventory.putAll(extractMapField(json, "inventory"));
        data.skills.putAll(extractMapField(json, "skills"));
        data.skillXp.putAll(extractMapField(json, "skillXp"));
        data.equipment.putAll(extractMapField(json, "equipment"));
        return data;
    }

    private static void appendStringField(StringBuilder sb, String name, String value, boolean trailingComma) {
        sb.append("  \"").append(name).append("\": \"")
                .append(escape(defaultString(value, ""))).append("\"");
        if (trailingComma) sb.append(',');
        sb.append('\n');
    }

    private static void appendIntField(StringBuilder sb, String name, int value, boolean trailingComma) {
        sb.append("  \"").append(name).append("\": ").append(value);
        if (trailingComma) sb.append(',');
        sb.append('\n');
    }

    private static void appendStringMapField(StringBuilder sb, String name,
                                              Map<String, String> map, boolean trailingComma) {
        sb.append("  \"").append(name).append("\": {\n");
        int index = 0;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            sb.append("    \"").append(escape(entry.getKey())).append("\": \"")
              .append(escape(entry.getValue())).append("\"");
            if (++index < map.size()) sb.append(',');
            sb.append('\n');
        }
        sb.append("  }");
        if (trailingComma) sb.append(',');
        sb.append('\n');
    }

    private static void appendMapField(StringBuilder sb, String name, Map<String, Integer> map, boolean trailingComma) {
        sb.append("  \"").append(name).append("\": {\n");
        int index = 0;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            sb.append("    \"").append(escape(entry.getKey())).append("\": ").append(entry.getValue());
            if (++index < map.size()) sb.append(',');
            sb.append('\n');
        }
        sb.append("  }");
        if (trailingComma) sb.append(',');
        sb.append('\n');
    }

    private static String extractStringField(String json, String fieldName) {
        Matcher matcher = Pattern.compile(String.format(STRING_FIELD_PATTERN.pattern(), Pattern.quote(fieldName)))
                .matcher(json);
        return matcher.find() ? unescape(matcher.group(1)) : null;
    }

    private static int extractIntField(String json, String fieldName, int fallback) {
        Matcher matcher = Pattern.compile(String.format(INT_FIELD_PATTERN.pattern(), Pattern.quote(fieldName)))
                .matcher(json);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private static Map<String, String> extractStringMapField(String json, String fieldName) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        String body = extractObjectBody(json, fieldName);
        if (body == null || body.isBlank()) return map;
        Matcher matcher = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        while (matcher.find()) {
            map.put(unescape(matcher.group(1)), unescape(matcher.group(2)));
        }
        return map;
    }

    private static Map<String, Integer> extractMapField(String json, String fieldName) {
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
        String body = extractObjectBody(json, fieldName);
        if (body == null || body.isBlank()) {
            return map;
        }

        Matcher matcher = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(-?\\d+)").matcher(body);
        while (matcher.find()) {
            map.put(unescape(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        }
        return map;
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
            if (c == '}' && --depth == 0) {
                return json.substring(start + 1, i);
            }
        }
        return null;
    }

    private static String defaultString(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
