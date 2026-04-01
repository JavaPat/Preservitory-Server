package com.classic.preservitory.server.world;

import com.classic.preservitory.server.definitions.NpcDefinition;
import com.classic.preservitory.server.definitions.NpcDefinitionManager;
import com.classic.preservitory.server.npc.NPCData;
import com.classic.preservitory.server.spawns.SpawnEntry;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Manages all live NPC instances and drives their movement each tick.
 *
 * Protocol:
 *   NPCS id x y name shopkeeper;...
 *
 * The full snapshot is sent on connect and re-broadcast whenever any wandering
 * NPC moves.  Static NPCs never trigger a re-broadcast.
 */
public class NPCManager {

    private static final int TILE_SIZE = TreeManager.TILE_SIZE;

    /** Insertion-order map so snapshot entries are always in a stable sequence. */
    private final LinkedHashMap<String, NPCData> npcs = new LinkedHashMap<>();
    private final EntityMovementSystem movementSystem = new EntityMovementSystem();

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public NPCManager(List<SpawnEntry> spawns) {
        for (SpawnEntry spawn : spawns) {
            NpcDefinition def         = NpcDefinitionManager.get(spawn.definitionId);
            int           radiusPx    = def.wanderRadiusTiles * TILE_SIZE;
            npcs.put(spawn.id, new NPCData(
                    spawn.id, def.id, spawn.x, spawn.y,
                    def.name, def.shopkeeper,
                    def.wander, radiusPx));
        }
        System.out.println("[NPCManager] Loaded " + npcs.size() + " NPCs.");
    }

    // -----------------------------------------------------------------------
    //  Tick
    // -----------------------------------------------------------------------

    /**
     * Advance all wandering NPCs by one time step.
     *
     * @return {@code true} if any NPC position changed (a snapshot broadcast is needed)
     */
    public boolean update(long deltaTimeMs) {
        return movementSystem.update(npcs.values(), deltaTimeMs);
    }

    // -----------------------------------------------------------------------
    //  Protocol
    // -----------------------------------------------------------------------

    /**
     * Build the full NPC snapshot string.
     *
     * Positions are truncated to integer pixels for the wire format.
     * Format: {@code NPCS id x y name shopkeeper;...}
     */
    public String buildSnapshot() {
        StringBuilder sb = new StringBuilder("NPCS");
        for (NPCData n : npcs.values()) {
            sb.append(' ')
              .append(n.id).append(' ')
              .append((int) n.x).append(' ')
              .append((int) n.y).append(' ')
              .append(n.name).append(' ')
              .append(n.shopkeeper)
              .append(';');
        }
        return sb.toString();
    }

    public NPCData getNpc(String id) {
        return npcs.get(id);
    }
}
