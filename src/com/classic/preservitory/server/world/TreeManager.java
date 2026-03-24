package com.classic.preservitory.server.world;

import com.classic.preservitory.server.objects.TreeData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

public class TreeManager {

    // -----------------------------------------------------------------------
    //  Constants
    // -----------------------------------------------------------------------

    public static final int TILE_SIZE    = 32;
    public static final int REGION_TILES = 8;
    public static final int REGION_PX    = TILE_SIZE * REGION_TILES; // 256
    public static final int VIEW_RADIUS  = 2;

    private static final long RESPAWN_DELAY_MS = 12_000L;
    private static final String MAP_NAME = "starter_map.json";
    private static final Pattern OBJECT_PATTERN = Pattern.compile("\\{([^{}]*)\\}");
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern X_PATTERN = Pattern.compile("\"x\"\\s*:\\s*(-?\\d+)");
    private static final Pattern Y_PATTERN = Pattern.compile("\"y\"\\s*:\\s*(-?\\d+)");

    // -----------------------------------------------------------------------
    //  Storage
    // -----------------------------------------------------------------------

    private final ConcurrentHashMap<String, TreeData> allTrees  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RegionKey, ConcurrentHashMap<String, TreeData>> regionMap
            = new ConcurrentHashMap<>();

    private final TreePersistence persistence = new TreePersistence();

    // -----------------------------------------------------------------------
    //  Construction — load from map JSON
    // -----------------------------------------------------------------------

    public TreeManager() {
        List<TreeData> mapTrees = loadTreesFromMap();
        for (TreeData t : mapTrees) {
            allTrees.put(t.id, t);
            bucketFor(t.x, t.y).put(t.id, t);
        }
        // Startup state is authoritative from starter_map.json only.
        // Persisted tree state is intentionally not loaded or merged here.
        System.out.println("[TreeManager] Loaded " + allTrees.size() + " trees from map.");
    }

    private List<TreeData> loadTreesFromMap() {
        Path mapPath = resolveMapPath();
        try {
            String json = Files.readString(mapPath, StandardCharsets.UTF_8);
            String objects = extractObjectsArray(json);

            List<TreeData> trees = new ArrayList<>();
            Matcher matcher = OBJECT_PATTERN.matcher(objects);
            int treeIndex = 0;

            while (matcher.find()) {
                String object = matcher.group(1);
                String id = extractString(object, ID_PATTERN);
                if (!"tree".equals(id)) continue;

                int x = extractInt(object, X_PATTERN);
                int y = extractInt(object, Y_PATTERN);
                trees.add(new TreeData("T" + treeIndex++, x, y));
            }

            if (trees.isEmpty()) {
                throw new IllegalStateException("No tree objects found in " + mapPath);
            }

            return trees;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load trees from " + mapPath, e);
        }
    }

    private Path resolveMapPath() {
        List<Path> candidates = List.of(
                Paths.get("cache", "maps", MAP_NAME),
                Paths.get("..", "Preservitory", "cache", "maps", MAP_NAME)
        );

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("Could not find map file " + MAP_NAME);
    }

    private String extractObjectsArray(String json) {
        String marker = "\"objects\"";
        int markerIndex = json.indexOf(marker);
        if (markerIndex == -1) {
            throw new IllegalStateException("Map JSON is missing objects array");
        }

        int arrayStart = json.indexOf('[', markerIndex);
        if (arrayStart == -1) {
            throw new IllegalStateException("Map JSON objects entry is not an array");
        }

        int depth = 0;
        for (int i = arrayStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            if (c == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(arrayStart + 1, i);
                }
            }
        }

        throw new IllegalStateException("Map JSON objects array is unterminated");
    }

    private String extractString(String object, Pattern pattern) {
        Matcher matcher = pattern.matcher(object);
        return matcher.find() ? matcher.group(1) : null;
    }

    private int extractInt(String object, Pattern pattern) {
        Matcher matcher = pattern.matcher(object);
        if (!matcher.find()) {
            throw new IllegalStateException("Missing numeric field in object: {" + object + "}");
        }
        return Integer.parseInt(matcher.group(1));
    }

    // -----------------------------------------------------------------------
    //  Region utilities
    // -----------------------------------------------------------------------

    public static RegionKey getRegionForPosition(int worldX, int worldY) {
        return new RegionKey(
                Math.floorDiv(worldX, REGION_PX),
                Math.floorDiv(worldY, REGION_PX)
        );
    }

    public Set<RegionKey> getVisibleRegions(RegionKey center) {
        Set<RegionKey> result = new HashSet<>();
        for (int dx = -VIEW_RADIUS; dx <= VIEW_RADIUS; dx++) {
            for (int dy = -VIEW_RADIUS; dy <= VIEW_RADIUS; dy++) {
                result.add(new RegionKey(center.regionX + dx, center.regionY + dy));
            }
        }
        return result;
    }

    private ConcurrentHashMap<String, TreeData> bucketFor(int worldX, int worldY) {
        return regionMap.computeIfAbsent(getRegionForPosition(worldX, worldY),
                k -> new ConcurrentHashMap<>());
    }

    // -----------------------------------------------------------------------
    //  Queries
    // -----------------------------------------------------------------------

    public TreeData getTree(String id) {
        return allTrees.get(id);
    }

    public Collection<TreeData> getTreesInRegion(RegionKey key) {
        ConcurrentHashMap<String, TreeData> bucket = regionMap.get(key);
        return bucket == null ? Collections.emptyList() : bucket.values();
    }

    // -----------------------------------------------------------------------
    //  Protocol builders
    // -----------------------------------------------------------------------

    public String buildStateForRegions(Set<RegionKey> regions) {
        StringBuilder sb = new StringBuilder("TREES");
        for (RegionKey key : regions) {
            for (TreeData t : getTreesInRegion(key)) {
                if (!t.alive) continue;
                sb.append(' ').append(t.id)
                  .append(' ').append(t.x)
                  .append(' ').append(t.y)
                  .append(' ').append(1)
                  .append(';');
            }
        }
        return sb.toString();
    }

    public static String buildRemoveMessage(String treeId) {
        return "TREE_REMOVE " + treeId;
    }

    public static String buildAddMessage(TreeData t) {
        return "TREE_ADD " + t.id + " " + t.x + " " + t.y;
    }

    // -----------------------------------------------------------------------
    //  Mutation
    // -----------------------------------------------------------------------

    public boolean chopTree(String id) {
        TreeData tree = allTrees.get(id);
        if (tree == null) return false;
        synchronized (tree) {
            if (!tree.alive) return false;
            tree.alive       = false;
            tree.respawnTime = RESPAWN_DELAY_MS;
        }
        persistence.saveAllTrees(allTrees);
        return true;
    }

    public List<TreeData> update(long deltaTimeMs) {
        List<TreeData> respawned = new ArrayList<>();
        long elapsedMs = Math.max(0L, deltaTimeMs);

        for (TreeData tree : allTrees.values()) {
            synchronized (tree) {
                if (!tree.alive && tree.respawnTime > 0) {
                    tree.respawnTime = Math.max(0L, tree.respawnTime - elapsedMs);
                    if (tree.respawnTime == 0L) {
                        tree.alive = true;
                        respawned.add(tree);
                    }
                }
            }
        }

        if (!respawned.isEmpty()) {
            persistence.saveAllTrees(allTrees);
        }

        return respawned;
    }
}
