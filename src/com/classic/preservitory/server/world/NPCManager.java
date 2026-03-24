package com.classic.preservitory.server.world;

import com.classic.preservitory.server.npc.NPCData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Loads stationary NPCs from {@code starter_map.json} and builds the
 * authoritative snapshot sent to every client on connect.
 *
 * Protocol:
 *   NPCS id x y name shopkeeper;...
 *
 * NPCs are static for now — no AI, no movement.  The full snapshot is
 * sent once on connect; no delta updates are needed while NPCs don't move.
 */
public class NPCManager {

    private static final String MAP_NAME = "starter_map.json";
    private static final int TILE_SIZE = 32;
    private static final int GUIDE_SAFE_X = 19 * TILE_SIZE;
    private static final int GUIDE_SAFE_Y = 10 * TILE_SIZE;

    private static final Pattern OBJECT_PATTERN     = Pattern.compile("\\{([^{}]*)\\}");
    private static final Pattern ID_PATTERN         = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern X_PATTERN          = Pattern.compile("\"x\"\\s*:\\s*(-?\\d+)");
    private static final Pattern Y_PATTERN          = Pattern.compile("\"y\"\\s*:\\s*(-?\\d+)");
    private static final Pattern NAME_PATTERN       = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SHOPKEEPER_PATTERN = Pattern.compile("\"shopkeeper\"\\s*:\\s*(true|false)");

    /** Insertion-order map so snapshot entries are always in a stable sequence. */
    private final LinkedHashMap<String, NPCData> npcs = new LinkedHashMap<>();

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public NPCManager() {
        loadFromMap();
        System.out.println("[NPCManager] Loaded " + npcs.size() + " NPCs.");
    }

    private void loadFromMap() {
        Path mapPath = resolveMapPath();
        try {
            String json     = Files.readString(mapPath, StandardCharsets.UTF_8);
            Set<String> blockedTiles = loadBlockedTreeTiles(json);
            String npcArray = extractNamedArray(json, "npcs");
            if (npcArray == null) return;

            Matcher m = OBJECT_PATTERN.matcher(npcArray);
            while (m.find()) {
                String obj        = m.group(1);
                String id         = extractString(obj, ID_PATTERN);
                String name       = extractString(obj, NAME_PATTERN);
                if (id == null || name == null) continue;

                int     x          = extractInt(obj, X_PATTERN);
                int     y          = extractInt(obj, Y_PATTERN);
                String  skStr      = extractString(obj, SHOPKEEPER_PATTERN);
                boolean shopkeeper = "true".equals(skStr);
                int[]   resolved   = resolveNpcPosition(id, x, y, blockedTiles);

                blockedTiles.add(tileKey(resolved[0], resolved[1]));
                npcs.put(id, new NPCData(id, resolved[0], resolved[1], name, shopkeeper));
            }
        } catch (IOException e) {
            System.err.println("[NPCManager] Could not load NPCs: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    //  Protocol
    // -----------------------------------------------------------------------

    /**
     * Build the full NPC snapshot string to send on connect.
     *
     * Format: {@code NPCS id x y name shopkeeper;...}
     * Returns {@code "NPCS"} (no entries) if no NPCs are defined — client
     * handles an empty payload gracefully.
     */
    public String buildSnapshot() {
        StringBuilder sb = new StringBuilder("NPCS");
        for (NPCData n : npcs.values()) {
            sb.append(' ')
              .append(n.id).append(' ')
              .append(n.x).append(' ')
              .append(n.y).append(' ')
              .append(n.name).append(' ')
              .append(n.shopkeeper)
              .append(';');
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    //  Path resolution — same candidates used by TreeManager / RockManager
    // -----------------------------------------------------------------------

    private Path resolveMapPath() {
        List<Path> candidates = List.of(
                Paths.get("cache", "maps", MAP_NAME),
                Paths.get("..", "Preservitory", "cache", "maps", MAP_NAME)
        );
        for (Path p : candidates) {
            if (Files.exists(p)) return p;
        }
        throw new IllegalStateException("[NPCManager] Cannot find " + MAP_NAME);
    }

    // -----------------------------------------------------------------------
    //  JSON helpers
    // -----------------------------------------------------------------------

    private String extractNamedArray(String json, String arrayName) {
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

    private String extractString(String obj, Pattern p) {
        Matcher m = p.matcher(obj);
        return m.find() ? m.group(1) : null;
    }

    private int extractInt(String obj, Pattern p) {
        Matcher m = p.matcher(obj);
        if (!m.find()) throw new IllegalStateException(
                "Missing numeric field matching " + p.pattern());
        return Integer.parseInt(m.group(1));
    }

    private Set<String> loadBlockedTreeTiles(String json) {
        Set<String> blocked = new HashSet<>();
        String objectArray = extractNamedArray(json, "objects");
        if (objectArray == null) return blocked;

        Matcher m = OBJECT_PATTERN.matcher(objectArray);
        while (m.find()) {
            String obj = m.group(1);
            String id = extractString(obj, ID_PATTERN);
            if (!"tree".equals(id)) continue;

            int x = extractInt(obj, X_PATTERN);
            int y = extractInt(obj, Y_PATTERN);
            blocked.add(tileKey(x, y));
        }
        return blocked;
    }

    private int[] resolveNpcPosition(String id, int x, int y, Set<String> blockedTiles) {
        if ("guide".equals(id)) {
            return new int[]{GUIDE_SAFE_X, GUIDE_SAFE_Y};
        }

        if (!blockedTiles.contains(tileKey(x, y))) {
            return new int[]{x, y};
        }

        int[][] offsets = {
                { TILE_SIZE, 0 }, { -TILE_SIZE, 0 }, { 0, TILE_SIZE }, { 0, -TILE_SIZE },
                { TILE_SIZE, TILE_SIZE }, { TILE_SIZE, -TILE_SIZE },
                { -TILE_SIZE, TILE_SIZE }, { -TILE_SIZE, -TILE_SIZE }
        };
        for (int[] offset : offsets) {
            int nx = x + offset[0];
            int ny = y + offset[1];
            if (!blockedTiles.contains(tileKey(nx, ny))) {
                return new int[]{nx, ny};
            }
        }

        return new int[]{x + TILE_SIZE, y};
    }

    private String tileKey(int x, int y) {
        return x + ":" + y;
    }
}
