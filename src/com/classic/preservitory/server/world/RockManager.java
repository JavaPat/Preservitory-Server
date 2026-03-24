package com.classic.preservitory.server.world;

import com.classic.preservitory.server.objects.RockData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;

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

    private static final long   RESPAWN_DELAY_MS = 8_000L;
    private static final String MAP_NAME         = "starter_map.json";

    private static final Pattern OBJECT_PATTERN = Pattern.compile("\\{([^{}]*)\\}");
    private static final Pattern ID_PATTERN     = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern X_PATTERN      = Pattern.compile("\"x\"\\s*:\\s*(-?\\d+)");
    private static final Pattern Y_PATTERN      = Pattern.compile("\"y\"\\s*:\\s*(-?\\d+)");

    // -----------------------------------------------------------------------
    //  Storage
    // -----------------------------------------------------------------------

    private final ConcurrentHashMap<String, RockData> allRocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RegionKey, ConcurrentHashMap<String, RockData>> regionMap
            = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public RockManager() {
        List<RockData> mapRocks = loadRocksFromMap();
        for (RockData r : mapRocks) {
            allRocks.put(r.id, r);
            bucketFor(r.x, r.y).put(r.id, r);
        }
        System.out.println("[RockManager] Loaded " + allRocks.size() + " rocks from map.");
    }

    private List<RockData> loadRocksFromMap() {
        Path mapPath = resolveMapPath();
        try {
            String json    = Files.readString(mapPath, StandardCharsets.UTF_8);
            String objects = extractObjectsArray(json);

            List<RockData> rocks = new ArrayList<>();
            Matcher matcher      = OBJECT_PATTERN.matcher(objects);
            int rockIndex        = 0;

            while (matcher.find()) {
                String obj = matcher.group(1);
                String id  = extractString(obj, ID_PATTERN);
                if (!"rock".equals(id)) continue;

                int x = extractInt(obj, X_PATTERN);
                int y = extractInt(obj, Y_PATTERN);
                rocks.add(new RockData("R" + rockIndex++, x, y));
            }

            return rocks;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load rocks from " + mapPath, e);
        }
    }

    private Path resolveMapPath() {
        List<Path> candidates = List.of(
                Paths.get("cache", "maps", MAP_NAME),
                Paths.get("..", "Preservitory", "cache", "maps", MAP_NAME)
        );
        for (Path p : candidates) {
            if (Files.exists(p)) return p;
        }
        throw new IllegalStateException("Could not find map file " + MAP_NAME);
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
        return "ROCK_ADD " + r.id + " " + r.x + " " + r.y;
    }

    // -----------------------------------------------------------------------
    //  Mutation
    // -----------------------------------------------------------------------

    public boolean mineRock(String id) {
        RockData rock = allRocks.get(id);
        if (rock == null) return false;
        synchronized (rock) {
            if (!rock.alive) return false;
            rock.alive       = false;
            rock.respawnTime = RESPAWN_DELAY_MS;
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

    // -----------------------------------------------------------------------
    //  JSON helpers
    // -----------------------------------------------------------------------

    private String extractObjectsArray(String json) {
        int marker = json.indexOf("\"objects\"");
        if (marker == -1) throw new IllegalStateException("No 'objects' array in map JSON");
        int start = json.indexOf('[', marker);
        if (start == -1) throw new IllegalStateException("'objects' is not an array");
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            if (c == ']' && --depth == 0) return json.substring(start + 1, i);
        }
        throw new IllegalStateException("'objects' array is unterminated");
    }

    private String extractString(String obj, Pattern p) {
        Matcher m = p.matcher(obj);
        return m.find() ? m.group(1) : null;
    }

    private int extractInt(String obj, Pattern p) {
        Matcher m = p.matcher(obj);
        if (!m.find()) throw new IllegalStateException("Missing numeric field matching " + p.pattern());
        return Integer.parseInt(m.group(1));
    }
}
