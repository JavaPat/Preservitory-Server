package com.classic.preservitory.server.world;

import com.classic.preservitory.server.objects.LootData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages all ground-loot items currently in the world.
 *
 * Loot is spawned when an enemy dies and removed when a player picks it up.
 * All mutations are thread-safe via ConcurrentHashMap + AtomicInteger.
 */
public class LootManager {

    private final ConcurrentHashMap<String, LootData> loot   = new ConcurrentHashMap<>();
    private final AtomicInteger                        nextId = new AtomicInteger(1);
    private final Random                               rng    = new Random();

    // -----------------------------------------------------------------------
    //  Spawn
    // -----------------------------------------------------------------------

    /**
     * Spawn goblin drops at {@code (x, y)} and return the newly created entries.
     * Loot table: Coins (always, 3–15), Logs (25% chance).
     */
    public List<LootData> spawnGoblinDrops(int x, int y) {
        List<LootData> spawned = new ArrayList<>();
        spawned.add(spawn(x, y, "Coins", 3 + rng.nextInt(13)));
        if (rng.nextDouble() < 0.25) {
            spawned.add(spawn(x + 4, y + 4, "Logs", 1));
        }
        return spawned;
    }

    private LootData spawn(int x, int y, String name, int count) {
        String   id = "L" + nextId.getAndIncrement();
        LootData d  = new LootData(id, x, y, name, count);
        loot.put(id, d);
        return d;
    }

    // -----------------------------------------------------------------------
    //  Pickup
    // -----------------------------------------------------------------------

    /**
     * Remove a loot item and return it.
     * Returns {@code null} if the item no longer exists (already picked up by
     * another player or race condition).
     */
    public LootData pickup(String id) {
        return loot.remove(id);
    }

    /** Returns the loot item without removing it, or {@code null} if not present. */
    public LootData get(String id) {
        return loot.get(id);
    }

    // -----------------------------------------------------------------------
    //  Protocol builders
    // -----------------------------------------------------------------------

    /**
     * Full snapshot of all ground loot.
     * Format: {@code LOOT id x y name count; ...}
     */
    public String buildSnapshot() {
        StringBuilder sb = new StringBuilder("LOOT");
        for (LootData d : loot.values()) {
            sb.append(' ').append(d.id)
              .append(' ').append(d.x)
              .append(' ').append(d.y)
              .append(' ').append(d.itemName)
              .append(' ').append(d.count)
              .append(';');
        }
        return sb.toString();
    }

    /** Delta message for a newly spawned loot item. */
    public static String buildAddMessage(LootData d) {
        return "LOOT_ADD " + d.id + " " + d.x + " " + d.y
                + " " + d.itemName + " " + d.count;
    }

    /** Delta message for a removed loot item. */
    public static String buildRemoveMessage(String id) {
        return "LOOT_REMOVE " + id;
    }
}
