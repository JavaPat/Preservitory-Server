package com.classic.preservitory.server.gathering;

import com.classic.preservitory.server.definitions.ItemIds;
import com.classic.preservitory.server.definitions.ObjectDefinition;
import com.classic.preservitory.server.definitions.ObjectDefinitionManager;
import com.classic.preservitory.server.woodcutting.TreeDefinition;
import com.classic.preservitory.server.woodcutting.TreeDefinitionManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ResourceDefinitionManager {

    private final Map<Integer, ResourceDefinition> byObjectId;

    public ResourceDefinitionManager(TreeDefinitionManager treeDefinitionManager) {
        Map<Integer, ResourceDefinition> defs = new LinkedHashMap<>();

        for (ObjectDefinition objectDef : ObjectDefinitionManager.values()) {
            switch (objectDef.type) {
                case TREE -> defs.put(objectDef.id, fromTreeDefinition(treeDefinitionManager.get(objectDef.id)));
                case ROCK -> defs.put(objectDef.id, fromRockDefinition(objectDef));
            }
        }

        if (defs.isEmpty()) {
            throw new IllegalStateException("No resource definitions were created from object definitions.");
        }
        this.byObjectId = Map.copyOf(defs);
    }

    public ResourceDefinition get(int objectId) {
        ResourceDefinition def = byObjectId.get(objectId);
        if (def == null) {
            throw new IllegalStateException("ResourceDefinition not found for objectId=" + objectId);
        }
        return def;
    }

    public ResourceDefinition get(int objectId, SkillType skillType) {
        ResourceDefinition def = get(objectId);
        if (def.skillType != skillType) {
            throw new IllegalStateException("ResourceDefinition " + objectId
                    + " is " + def.skillType + ", expected " + skillType);
        }
        return def;
    }

    public Collection<ResourceDefinition> values() {
        return byObjectId.values();
    }

    private static ResourceDefinition fromTreeDefinition(TreeDefinition treeDef) {
        return new ResourceDefinition(
                treeDef.id,
                treeDef.levelRequired,
                treeDef.xp,
                treeDef.baseSuccessChance,
                treeDef.depletionChance,
                treeDef.respawnTimeMs,
                treeDef.logItemId,
                SkillType.WOODCUTTING
        );
    }

    private static ResourceDefinition fromRockDefinition(ObjectDefinition objectDef) {
        RockProfile profile = rockProfileFor(objectDef.key);
        return new ResourceDefinition(
                objectDef.id,
                profile.levelRequired,
                objectDef.xp > 0 ? objectDef.xp : profile.defaultXp,
                profile.successChance,
                profile.depletionChance,
                objectDef.respawnMs > 0 ? objectDef.respawnMs : profile.defaultRespawnMs,
                objectDef.resourceItemId > 0 ? objectDef.resourceItemId : ItemIds.ORE,
                SkillType.MINING
        );
    }

    private static RockProfile rockProfileFor(String key) {
        return switch (key) {
            case "copper_rock", "tin_rock" -> new RockProfile(1, 20, 1.0D, 1.0D, 8_000L);
            case "iron_rock" -> new RockProfile(1, 35, 1.0D, 1.0D, 10_000L);
            case "gold_rock" -> new RockProfile(1, 65, 1.0D, 1.0D, 15_000L);
            case "mithril_rock" -> new RockProfile(1, 80, 1.0D, 1.0D, 18_000L);
            case "adamant_rock" -> new RockProfile(1, 95, 1.0D, 1.0D, 22_000L);
            case "runite_rock" -> new RockProfile(1, 125, 1.0D, 1.0D, 30_000L);
            default -> new RockProfile(1, 20, 1.0D, 1.0D, 8_000L);
        };
    }

    private record RockProfile(int levelRequired,
                               int defaultXp,
                               double successChance,
                               double depletionChance,
                               long defaultRespawnMs) {
    }
}
