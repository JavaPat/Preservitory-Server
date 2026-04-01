package com.classic.preservitory.server.world;

import com.classic.preservitory.server.definitions.ObjectDefinition;
import com.classic.preservitory.server.definitions.ObjectDefinitionManager;
import com.classic.preservitory.server.objects.TreeData;
import com.classic.preservitory.server.spawns.SpawnEntry;

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

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public TreeManager(List<SpawnEntry> objectSpawns) {
        for (SpawnEntry spawn : objectSpawns) {
            ObjectDefinition def = ObjectDefinitionManager.get(spawn.definitionId);
            if (def.type != ObjectDefinition.Type.TREE) continue;
            TreeData t = new TreeData(spawn.id, def.key, def.id, spawn.x, spawn.y);
            allTrees.put(t.id, t);
            bucketFor(t.x, t.y).put(t.id, t);
        }
        if (allTrees.isEmpty()) {
            throw new IllegalStateException("No tree spawns found in cache/spawns/objects.json.");
        }
        System.out.println("[TreeManager] Loaded " + allTrees.size() + " trees.");
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
        ObjectDefinition def = ObjectDefinitionManager.get(tree.definitionId);
        long respawnTimeMs = def.respawnMs > 0 ? def.respawnMs : 12_000L;
        return chopTree(id, respawnTimeMs);
    }

    public boolean chopTree(String id, long respawnTimeMs) {
        TreeData tree = allTrees.get(id);
        if (tree == null) return false;
        synchronized (tree) {
            if (!tree.alive) return false;
            tree.alive       = false;
            tree.respawnTime = Math.max(1L, respawnTimeMs);
        }
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

        return respawned;
    }
}
