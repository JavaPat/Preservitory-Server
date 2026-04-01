package com.classic.preservitory.server.woodcutting;

import com.classic.preservitory.server.definitions.ItemIds;
import com.classic.preservitory.server.definitions.ObjectDefinition;
import com.classic.preservitory.server.definitions.ObjectDefinitionManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TreeDefinitionManager {

    private final Map<Integer, TreeDefinition> byObjectId;

    public TreeDefinitionManager() {
        Map<Integer, TreeDefinition> defs = new LinkedHashMap<>();
        for (ObjectDefinition objectDef : ObjectDefinitionManager.values()) {
            if (objectDef.type != ObjectDefinition.Type.TREE) {
                continue;
            }
            defs.put(objectDef.id, buildDefinition(objectDef));
        }
        if (defs.isEmpty()) {
            throw new IllegalStateException("No tree definitions were created from object definitions.");
        }
        this.byObjectId = Map.copyOf(defs);
    }

    public TreeDefinition get(int objectDefinitionId) {
        TreeDefinition def = byObjectId.get(objectDefinitionId);
        if (def == null) {
            throw new IllegalStateException("TreeDefinition not found for objectDefinitionId=" + objectDefinitionId);
        }
        return def;
    }

    public Collection<TreeDefinition> values() {
        return byObjectId.values();
    }

    private static TreeDefinition buildDefinition(ObjectDefinition objectDef) {
        TreeProfile profile = profileFor(objectDef.key);
        int logItemId = objectDef.resourceItemId > 0 ? objectDef.resourceItemId : ItemIds.LOGS;
        int xp = objectDef.xp > 0 ? objectDef.xp : profile.defaultXp;
        return new TreeDefinition(objectDef.id, logItemId, xp, profile.levelRequired,
                profile.baseSuccessChance, profile.depletionChance,
                objectDef.respawnMs > 0 ? objectDef.respawnMs : profile.defaultRespawnTimeMs);
    }

    private static TreeProfile profileFor(String key) {
        return switch (key) {
            case "tree" -> new TreeProfile(1, 25, 0.60D, 1.0D, 12_000L);
            case "oak_tree" -> new TreeProfile(15, 35, 0.45D, 0.18D, 18_000L);
            case "willow_tree" -> new TreeProfile(30, 67, 0.55D, 0.30D, 10_000L);
            case "maple_tree" -> new TreeProfile(45, 100, 0.45D, 0.40D, 15_000L);
            case "yew_tree" -> new TreeProfile(60, 175, 0.35D, 0.50D, 20_000L);
            default -> new TreeProfile(1, 25, 0.55D, 0.24D, 10_000L);
        };
    }

    private record TreeProfile(int levelRequired, int defaultXp,
                               double baseSuccessChance, double depletionChance,
                               long defaultRespawnTimeMs) {
    }
}
