package com.classic.preservitory.server.world;

import com.classic.preservitory.server.content.MapContentLoader;
import com.classic.preservitory.server.content.NpcDefinition;
import com.classic.preservitory.server.content.NpcDefinitionLoader;
import com.classic.preservitory.server.content.ObjectTypeDefinition;
import com.classic.preservitory.server.content.ObjectTypeLoader;
import com.classic.preservitory.server.npc.NPCData;

import java.util.*;

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

    private static final int TILE_SIZE = 32;

    /** Insertion-order map so snapshot entries are always in a stable sequence. */
    private final LinkedHashMap<String, NPCData> npcs = new LinkedHashMap<>();
    private final Map<String, NpcDefinition> definitions = NpcDefinitionLoader.loadAll();
    private final Map<String, ObjectTypeDefinition> objectDefinitions = ObjectTypeLoader.loadAll();

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public NPCManager() {
        loadFromMap();
        System.out.println("[NPCManager] Loaded " + npcs.size() + " NPCs.");
    }

    private void loadFromMap() {
        Set<String> blockedTiles = loadBlockedTreeTiles();

        for (MapContentLoader.MapNpcSpawn spawn : MapContentLoader.loadNpcs()) {
            NpcDefinition definition = definitions.get(spawn.definitionId());
            if (definition == null) {
                continue;
            }

            int[] resolved = resolveNpcPosition(spawn.x(), spawn.y(), blockedTiles);
            blockedTiles.add(tileKey(resolved[0], resolved[1]));
            npcs.put(spawn.id(), new NPCData(
                    spawn.id(),
                    spawn.definitionId(),
                    resolved[0],
                    resolved[1],
                    definition.name,
                    definition.shopkeeper
            ));
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

    public NPCData getNpc(String id) {
        return npcs.get(id);
    }

    public NpcDefinition getDefinition(String definitionId) {
        return definitions.get(definitionId);
    }

    private Set<String> loadBlockedTreeTiles() {
        Set<String> blocked = new HashSet<>();
        for (MapContentLoader.MapObjectSpawn spawn : MapContentLoader.loadObjects()) {
            ObjectTypeDefinition def = objectDefinitions.get(spawn.definitionId());
            if (def != null && def.category.equals("tree")) {
                blocked.add(tileKey(spawn.x(), spawn.y()));
            }
        }
        return blocked;
    }

    private int[] resolveNpcPosition(int x, int y, Set<String> blockedTiles) {
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
