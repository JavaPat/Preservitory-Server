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
 * Reads every {@code *.json} from {@code cache/dialogues/} and returns a
 * registry of {@link DialogueDefinition}s keyed by string id.
 *
 * <p>Supports two JSON formats:
 * <ul>
 *   <li><strong>Linear</strong> — has a {@code "lines"} array of strings.</li>
 *   <li><strong>Node-based</strong> — has a {@code "nodes"} array of objects.</li>
 * </ul>
 *
 * <p>Validation at load time (fail-fast):
 * <ul>
 *   <li>{@code npcId} must exist in {@link NpcDefinitionManager}</li>
 *   <li>Linear: {@code lines} must not be empty or blank</li>
 *   <li>Node-based: every {@code next} and option {@code next} reference must
 *       point to a declared node id (or be absent/null to terminate)</li>
 * </ul>
 *
 * <p>{@link NpcDefinitionManager} must be loaded before this loader is called.
 */
public final class DialogueDefinitionLoader {

    private static final Pattern ID_PATTERN     = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NPC_ID_PATTERN = Pattern.compile("\"npcId\"\\s*:\\s*(\\d+)");

    private DialogueDefinitionLoader() {}

    public static Map<String, DialogueDefinition> loadAll() {
        Map<String, DialogueDefinition> defs = new LinkedHashMap<>();

        for (Path dir : candidateDirs()) {
            if (!Files.isDirectory(dir)) continue;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path file : stream) {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    DialogueDefinition def = parse(json, file);
                    if (def != null) {
                        if (defs.containsKey(def.id)) {
                            throw new IllegalStateException(
                                    "[DialogueDefinitionLoader] Duplicate dialogue id '"
                                    + def.id + "' in " + file);
                        }
                        defs.put(def.id, def);
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load dialogue definitions from " + dir, e);
            }
            if (!defs.isEmpty()) return defs;
        }

        System.out.println("[DialogueDefinitionLoader] No dialogue definitions found.");
        return defs;
    }

    // -----------------------------------------------------------------------
    //  Top-level parsing dispatch
    // -----------------------------------------------------------------------

    private static DialogueDefinition parse(String json, Path file) {
        String id      = match(json, ID_PATTERN);
        String npcIdStr = match(json, NPC_ID_PATTERN);

        if (id == null || npcIdStr == null) {
            throw new IllegalStateException(
                    "[DialogueDefinitionLoader] Missing 'id' or 'npcId' in " + file);
        }

        int npcId;
        try {
            npcId = Integer.parseInt(npcIdStr);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "[DialogueDefinitionLoader] Invalid npcId in " + file);
        }

        if (!NpcDefinitionManager.exists(npcId)) {
            throw new IllegalStateException(
                    "[DialogueDefinitionLoader] Dialogue '" + id + "' in " + file
                    + " references unknown npcId=" + npcId + " — check cache/npcs/");
        }

        // Detect format: node-based takes precedence over lines-based
        boolean hasNodes = json.contains("\"nodes\"");
        if (hasNodes) {
            return parseNodeBased(json, id, npcId, file);
        }
        return parseLinear(json, id, npcId, file);
    }

    // -----------------------------------------------------------------------
    //  Linear (lines-based) parsing
    // -----------------------------------------------------------------------

    private static DialogueDefinition parseLinear(String json, String id, int npcId, Path file) {
        List<String> lines = extractStringArray(json, "lines");
        if (lines.isEmpty()) {
            throw new IllegalStateException(
                    "[DialogueDefinitionLoader] Dialogue '" + id + "' in " + file
                    + " has an empty 'lines' array");
        }
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                throw new IllegalStateException(
                        "[DialogueDefinitionLoader] Dialogue '" + id + "' in " + file
                        + " has a blank line at index " + i);
            }
        }
        return new DialogueDefinition(id, npcId, lines);
    }

    // -----------------------------------------------------------------------
    //  Node-based parsing
    // -----------------------------------------------------------------------

    private static DialogueDefinition parseNodeBased(String json, String id, int npcId, Path file) {
        String nodesBody = extractArrayBody(json, "nodes");
        if (nodesBody == null || nodesBody.isBlank()) {
            throw new IllegalStateException(
                    "[DialogueDefinitionLoader] Dialogue '" + id + "' in " + file
                    + " has an empty or missing 'nodes' array");
        }

        List<String> nodeObjects = extractJsonObjects(nodesBody);
        if (nodeObjects.isEmpty()) {
            throw new IllegalStateException(
                    "[DialogueDefinitionLoader] Dialogue '" + id + "' in " + file
                    + " has no node objects in 'nodes' array");
        }

        Map<String, DialogueNode> nodes = new LinkedHashMap<>();
        String startNodeId = null;

        for (String nodeJson : nodeObjects) {
            DialogueNode node = parseNode(nodeJson, id, file);
            if (nodes.containsKey(node.id)) {
                throw new IllegalStateException(
                        "[DialogueDefinitionLoader] Dialogue '" + id + "' in " + file
                        + " has duplicate node id '" + node.id + "'");
            }
            nodes.put(node.id, node);
            if (startNodeId == null) startNodeId = node.id; // first node is entry point
        }

        // Validate all next/option references point to known node ids
        for (DialogueNode node : nodes.values()) {
            if (node.next != null && !nodes.containsKey(node.next)) {
                throw new IllegalStateException(
                        "[DialogueDefinitionLoader] Dialogue '" + id + "' in " + file
                        + " — node '" + node.id + "' has unknown 'next': '" + node.next + "'");
            }
            for (DialogueOption opt : node.options) {
                if (opt.next != null && !nodes.containsKey(opt.next)) {
                    throw new IllegalStateException(
                            "[DialogueDefinitionLoader] Dialogue '" + id + "' in " + file
                            + " — node '" + node.id + "' option '" + opt.text
                            + "' has unknown 'next': '" + opt.next + "'");
                }
            }
        }

        return new DialogueDefinition(id, npcId, nodes, startNodeId);
    }

    private static DialogueNode parseNode(String nodeJson, String dialogueId, Path file) {
        String nodeId = match(nodeJson, ID_PATTERN);
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalStateException(
                    "[DialogueDefinitionLoader] Dialogue '" + dialogueId + "' in " + file
                    + " — node is missing 'id' field: " + snippet(nodeJson));
        }

        String text = matchField(nodeJson, "text");
        if (text == null || text.isBlank()) {
            throw new IllegalStateException(
                    "[DialogueDefinitionLoader] Dialogue '" + dialogueId + "' in " + file
                    + " — node '" + nodeId + "' is missing 'text' field");
        }

        String next   = matchNullableField(nodeJson, "next");
        String action = matchField(nodeJson, "action");

        // Parse options (may be absent)
        List<DialogueOption> options = new ArrayList<>();
        String optionsBody = extractArrayBody(nodeJson, "options");
        if (optionsBody != null && !optionsBody.isBlank()) {
            for (String optJson : extractJsonObjects(optionsBody)) {
                String optText = matchField(optJson, "text");
                if (optText == null || optText.isBlank()) {
                    throw new IllegalStateException(
                            "[DialogueDefinitionLoader] Dialogue '" + dialogueId + "' in " + file
                            + " — node '" + nodeId + "' has an option with missing 'text'");
                }
                String optNext = matchNullableField(optJson, "next");
                options.add(new DialogueOption(optText, optNext));
            }
        }

        return new DialogueNode(nodeId, text, options, next, action);
    }

    // -----------------------------------------------------------------------
    //  JSON utilities
    // -----------------------------------------------------------------------

    /** Extract a JSON string array as a List<String>. */
    private static List<String> extractStringArray(String json, String fieldName) {
        List<String> result = new ArrayList<>();
        String body = extractArrayBody(json, fieldName);
        if (body == null || body.isBlank()) return result;
        Matcher m = Pattern.compile("\"([^\"]*)\"").matcher(body);
        while (m.find()) result.add(m.group(1));
        return result;
    }

    /** Extract the raw body of a JSON array (contents between [ and ]). */
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

    /**
     * Split an array body into individual JSON object strings.
     * Handles nested objects/arrays by tracking brace/bracket depth.
     */
    private static List<String> extractJsonObjects(String arrayBody) {
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < arrayBody.length()) {
            int start = arrayBody.indexOf('{', i);
            if (start == -1) break;
            int depth = 0;
            int end   = -1;
            for (int j = start; j < arrayBody.length(); j++) {
                char c = arrayBody.charAt(j);
                if      (c == '{') depth++;
                else if (c == '}' && --depth == 0) { end = j; break; }
            }
            if (end == -1) break;
            result.add(arrayBody.substring(start, end + 1));
            i = end + 1;
        }
        return result;
    }

    /** Match a simple string field value: {@code "fieldName": "value"}. Returns null if absent. */
    private static String matchField(String json, String fieldName) {
        Pattern p = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Match a nullable string field: {@code "fieldName": "value"} or {@code "fieldName": null}.
     * Returns the string value if present, or {@code null} if the field is absent or JSON null.
     */
    private static String matchNullableField(String json, String fieldName) {
        Pattern p = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*(?:\"([^\"]*)\"|null)");
        Matcher m = p.matcher(json);
        if (!m.find()) return null;
        return m.group(1); // null when JSON null was matched
    }

    private static String match(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String snippet(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }

    private static Path[] candidateDirs() {
        return new Path[]{
            Paths.get("cache", "dialogues"),
            Paths.get("..", "Preservitory-Server", "cache", "dialogues"),
            Paths.get("..", "Preservitory",        "cache", "dialogues")
        };
    }
}
