package com.classic.preservitory.server.world;

import com.classic.preservitory.server.content.MapContentLoader;
import com.classic.preservitory.server.content.ObjectTypeDefinition;
import com.classic.preservitory.server.content.ObjectTypeLoader;
import com.classic.preservitory.server.objects.TreeData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TreeManager {

    // -----------------------------------------------------------------------
    //  Constants
    // -----------------------------------------------------------------------

    public static final int TILE_SIZE    = 32;
    public static final int REGION_TILES = 8;
    public static final int REGION_PX    = TILE_SIZE * REGION_TILES; // 256
    public static final int VIEW_RADIUS  = 2;

    // -----------------------------------------------------------------------
    //  Storage
    // -----------------------------------------------------------------------

    private final ConcurrentHashMap<String, TreeData> allTrees  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RegionKey, ConcurrentHashMap<String, TreeData>> regionMap
            = new ConcurrentHashMap<>();

    private final TreePersistence persistence = new TreePersistence();
    private final Map<String, ObjectTypeDefinition> definitions = ObjectTypeLoader.loadAll();

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
        List<TreeData> trees = new ArrayList<>();

        for (MapContentLoader.MapObjectSpawn spawn : MapContentLoader.loadObjects()) {
            ObjectTypeDefinition def = definitions.get(spawn.definitionId());
            if (def == null || !"tree".equals(def.category)) {
                continue;
            }
            trees.add(new TreeData(spawn.id(), spawn.definitionId(), spawn.x(), spawn.y()));
        }

        if (trees.isEmpty()) {
            throw new IllegalStateException("No tree objects found in starter_map.json");
        }
        return trees;
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
                  .append(' ').append(t.typeId)
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
        return "TREE_ADD " + t.id + " " + t.typeId + " " + t.x + " " + t.y;
    }

    // -----------------------------------------------------------------------
    //  Mutation
    // -----------------------------------------------------------------------

    public boolean chopTree(String id) {
        TreeData tree = allTrees.get(id);
        if (tree == null) return false;
        synchronized (tree) {
            if (!tree.alive) return false;
            ObjectTypeDefinition def = definitions.get(tree.typeId);
            tree.alive       = false;
            tree.respawnTime = def != null && def.respawnMs > 0 ? def.respawnMs : 12_000L;
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
