package com.classic.preservitory.server.world;

import com.classic.preservitory.server.definitions.ObjectDefinition;
import com.classic.preservitory.server.definitions.ObjectDefinitionManager;
import com.classic.preservitory.server.objects.RockData;
import com.classic.preservitory.server.spawns.SpawnEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative manager for all rocks.
 *
 * Mirrors TreeManager exactly — same region bucketing, same protocol shape.
 *
 * Protocol:
 *   ROCKS   id x y 1;...    — full snapshot sent on connect (alive rocks only)
 *   ROCK_REMOVE id           — rock was mined
 *   ROCK_ADD    id x y       — rock respawned
 *
 * IDs are assigned R0, R1, … in JSON array order, matching the client's
 * MapLoader so offline-loaded rock IDs align with server IDs.
 */
public class RockManager {

    public static final int TILE_SIZE    = 32;
    public static final int REGION_TILES = 8;
    public static final int REGION_PX    = TILE_SIZE * REGION_TILES; // 256
    public static final int VIEW_RADIUS  = 2;

    // -----------------------------------------------------------------------
    //  Storage
    // -----------------------------------------------------------------------

    private final ConcurrentHashMap<String, RockData> allRocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RegionKey, ConcurrentHashMap<String, RockData>> regionMap
            = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public RockManager(List<SpawnEntry> objectSpawns) {
        for (SpawnEntry spawn : objectSpawns) {
            ObjectDefinition def = ObjectDefinitionManager.get(spawn.definitionId);
            if (def.type != ObjectDefinition.Type.ROCK) continue;
            RockData r = new RockData(spawn.id, def.key, def.id, spawn.x, spawn.y);
            allRocks.put(r.id, r);
            bucketFor(r.x, r.y).put(r.id, r);
        }
        if (allRocks.isEmpty()) {
            throw new IllegalStateException("No rock spawns found in cache/spawns/objects.json.");
        }
        System.out.println("[RockManager] Loaded " + allRocks.size() + " rocks.");
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

    private ConcurrentHashMap<String, RockData> bucketFor(int worldX, int worldY) {
        return regionMap.computeIfAbsent(getRegionForPosition(worldX, worldY),
                k -> new ConcurrentHashMap<>());
    }

    // -----------------------------------------------------------------------
    //  Queries
    // -----------------------------------------------------------------------

    public RockData getRock(String id) {
        return allRocks.get(id);
    }

    public Collection<RockData> getRocksInRegion(RegionKey key) {
        ConcurrentHashMap<String, RockData> bucket = regionMap.get(key);
        return bucket == null ? Collections.emptyList() : bucket.values();
    }

    // -----------------------------------------------------------------------
    //  Protocol builders
    // -----------------------------------------------------------------------

    public String buildStateForRegions(Set<RegionKey> regions) {
        StringBuilder sb = new StringBuilder("ROCKS");
        for (RegionKey key : regions) {
            for (RockData r : getRocksInRegion(key)) {
                if (!r.alive) continue;
                sb.append(' ').append(r.id)
                  .append(' ').append(r.typeId)
                  .append(' ').append(r.x)
                  .append(' ').append(r.y)
                  .append(' ').append(1)
                  .append(';');
            }
        }
        return sb.toString();
    }

    public static String buildRemoveMessage(String rockId) {
        return "ROCK_REMOVE " + rockId;
    }

    public static String buildAddMessage(RockData r) {
        return "ROCK_ADD " + r.id + " " + r.typeId + " " + r.x + " " + r.y;
    }

    // -----------------------------------------------------------------------
    //  Mutation
    // -----------------------------------------------------------------------

    public boolean mineRock(String id) {
        RockData rock = allRocks.get(id);
        if (rock == null) return false;
        ObjectDefinition def = ObjectDefinitionManager.get(rock.definitionId);
        long respawnMs = def.respawnMs > 0 ? def.respawnMs : 8_000L;
        return mineRock(id, respawnMs);
    }

    public boolean mineRock(String id, long respawnTimeMs) {
        RockData rock = allRocks.get(id);
        if (rock == null) return false;
        synchronized (rock) {
            if (!rock.alive) return false;
            rock.alive       = false;
            rock.respawnTime = Math.max(1L, respawnTimeMs);
        }
        return true;
    }

    public List<RockData> update(long deltaTimeMs) {
        List<RockData> respawned = new ArrayList<>();
        long elapsed = Math.max(0L, deltaTimeMs);

        for (RockData rock : allRocks.values()) {
            synchronized (rock) {
                if (!rock.alive && rock.respawnTime > 0) {
                    rock.respawnTime = Math.max(0L, rock.respawnTime - elapsed);
                    if (rock.respawnTime == 0L) {
                        rock.alive = true;
                        respawned.add(rock);
                    }
                }
            }
        }

        return respawned;
    }

}
