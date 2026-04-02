package com.classic.preservitory.server.world;

import com.classic.preservitory.server.definitions.EnemyDefinition;
import com.classic.preservitory.server.objects.LootData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages all ground-loot items currently in the world.
 *
 * Loot is spawned when an enemy dies and removed when a player picks it up.
 * Items are referenced by integer itemId (see {@link ItemIds}).
 */
public class LootManager {

    private static final long PRIVATE_MS  = 60_000L;   // owner-only for 60 s
    private static final long DESPAWN_MS  = 120_000L;  // gone after 60 s public (120 s total)

    private final ConcurrentHashMap<String, LootData> loot   = new ConcurrentHashMap<>();
    private final AtomicInteger                        nextId = new AtomicInteger(1);
    private final Random                               rng    = new Random();

    // -----------------------------------------------------------------------
    //  Spawn
    // -----------------------------------------------------------------------

    /**
     * Spawn drops for a killed enemy using its definition's drop table.
     * Each entry is rolled independently; amount is sampled from [minAmount, maxAmount].
     */
    public List<LootData> spawnDrops(int x, int y, EnemyDefinition def, String ownerId) {
        List<LootData> spawned = new ArrayList<>();
        int offset = 0;
        for (EnemyDefinition.DropEntry entry : def.dropTable) {
            if (rng.nextDouble() < entry.chance) {
                int amount = entry.minAmount
                        + (entry.maxAmount > entry.minAmount
                           ? rng.nextInt(entry.maxAmount - entry.minAmount + 1)
                           : 0);
                spawned.add(spawn(x + offset, y + offset, entry.itemId, amount, ownerId));
                offset += 4; // slight offset so stacked drops don't overlap
            }
        }
        return spawned;
    }

    /** Spawn a single player-dropped item. Returns the new LootData. */
    public LootData spawnDrop(int x, int y, int itemId, int count, String ownerId) {
        return spawn(x, y, itemId, count, ownerId);
    }

    private LootData spawn(int x, int y, int itemId, int count, String ownerId) {
        String   id = "L" + nextId.getAndIncrement();
        LootData d  = new LootData(id, x, y, itemId, count, ownerId, System.currentTimeMillis());
        loot.put(id, d);
        return d;
    }

    // -----------------------------------------------------------------------
    //  Pickup
    // -----------------------------------------------------------------------

    /** Remove a loot item and return it, or null if already gone. */
    public LootData pickup(String id) {
        return loot.remove(id);
    }

    /** Return without removing, or null if not present. */
    public LootData get(String id) {
        return loot.get(id);
    }

    /** Restore a loot item that was removed but could not be awarded. */
    public void restore(LootData d) {
        if (d != null) loot.put(d.id, d);
    }

    public boolean canSee(String playerId, LootData d, long now) {
        return d != null && (Objects.equals(d.ownerId, playerId) || isPublic(d, now));
    }

    public boolean canPickup(String playerId, LootData d, long now) {
        return canSee(playerId, d, now);
    }

    public boolean isPublic(LootData d, long now) {
        return d != null && now - d.spawnTime >= PRIVATE_MS;
    }

    /** Remove and return every loot item whose despawn timer has expired. */
    public List<LootData> collectExpired(long now) {
        List<LootData> expired = new ArrayList<>();
        for (LootData d : loot.values()) {
            if (now - d.spawnTime <= DESPAWN_MS) continue;
            if (loot.remove(d.id, d)) {
                expired.add(d);
            }
        }
        return expired;
    }

    /** Return loot items whose private timer elapsed during the last tick window. */
    public List<LootData> collectNewlyPublic(long fromTimeExclusive, long toTimeInclusive) {
        List<LootData> newlyPublic = new ArrayList<>();
        for (LootData d : loot.values()) {
            long ageFrom = fromTimeExclusive - d.spawnTime;
            long ageTo = toTimeInclusive - d.spawnTime;
            if (ageFrom < PRIVATE_MS && ageTo >= PRIVATE_MS) {
                newlyPublic.add(d);
            }
        }
        return newlyPublic;
    }

    // -----------------------------------------------------------------------
    //  Protocol builders
    // -----------------------------------------------------------------------

    /**
     * Full snapshot: {@code LOOT id x y itemId count; ...}
     */
    public String buildSnapshot() {
        StringBuilder sb = new StringBuilder("LOOT");
        for (LootData d : loot.values()) {
            sb.append(' ').append(d.id)
              .append(' ').append(d.x)
              .append(' ').append(d.y)
              .append(' ').append(d.itemId)
              .append(' ').append(d.count)
              .append(';');
        }
        return sb.toString();
    }

    public String buildSnapshotForPlayer(String playerId, long now) {
        StringBuilder sb = new StringBuilder("LOOT");
        for (LootData d : loot.values()) {
            if (!canSee(playerId, d, now)) continue;
            sb.append(' ').append(d.id)
              .append(' ').append(d.x)
              .append(' ').append(d.y)
              .append(' ').append(d.itemId)
              .append(' ').append(d.count)
              .append(';');
        }
        return sb.toString();
    }

    /** Delta add: {@code GROUND_ITEM_ADD id x y itemId count} */
    public static String buildAddMessage(LootData d) {
        return "GROUND_ITEM_ADD " + d.id + " " + d.x + " " + d.y
                + " " + d.itemId + " " + d.count;
    }

    /** Delta remove: {@code GROUND_ITEM_REMOVE id} */
    public static String buildRemoveMessage(String id) {
        return "GROUND_ITEM_REMOVE " + id;
    }
}
