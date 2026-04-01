package com.classic.preservitory.server.definitions;

import com.classic.preservitory.server.shops.ShopManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cross-reference and integrity validator run once at startup, after all definitions
 * and shops are loaded but before any world managers are constructed.
 *
 * Collects ALL errors before throwing so every broken reference is visible at once.
 *
 * NOTE: Duplicate numeric IDs cannot be detected here because the loader's
 * {@code Map.put(id, def)} silently overwrites on collision.  Duplicate keys
 * within a type ARE detected below.
 */
public final class DefinitionValidator {

    private DefinitionValidator() {}

    /**
     * Validate all loaded definitions.
     *
     * @throws IllegalStateException listing every validation error found.
     */
    public static void validateAll(ShopManager shopManager) {
        List<String> errors = new ArrayList<>();

        checkNonEmpty(errors);
        checkNoDuplicateKeys(errors);
        validateItemStats(errors);
        validateEnemyStats(errors);
        validateEnemyDropTables(errors);
        validateObjectStats(errors);
        validateObjectResources(errors);
        validateNpcShopRefs(errors, shopManager);

        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder(
                    "[DefinitionValidator] Startup aborted — " + errors.size() + " error(s) found:\n");
            for (String e : errors) sb.append("  - ").append(e).append('\n');
            throw new IllegalStateException(sb.toString());
        }

        System.out.println("[DefinitionValidator] All definitions valid ("
                + ItemDefinitionManager.values().size()    + " items, "
                + EnemyDefinitionManager.values().size()   + " enemies, "
                + ObjectDefinitionManager.values().size()  + " objects, "
                + NpcDefinitionManager.values().size()     + " npcs).");
    }

    // -----------------------------------------------------------------------
    //  Registry presence
    // -----------------------------------------------------------------------

    private static void checkNonEmpty(List<String> errors) {
        if (ItemDefinitionManager.values().isEmpty())
            errors.add("No item definitions loaded — check cache/items/");
        if (EnemyDefinitionManager.values().isEmpty())
            errors.add("No enemy definitions loaded — check cache/enemies/");
        if (ObjectDefinitionManager.values().isEmpty())
            errors.add("No object definitions loaded — check cache/objects/");
        if (NpcDefinitionManager.values().isEmpty())
            errors.add("No NPC definitions loaded — check cache/npcs/");
    }

    // -----------------------------------------------------------------------
    //  Duplicate key detection
    // -----------------------------------------------------------------------

    // Items are identified only by numeric id — no string key field to check.
    private static void checkNoDuplicateKeys(List<String> errors) {
        checkEnemyKeys(errors);
        checkObjectKeys(errors);
        checkNpcKeys(errors);
    }

    private static void checkEnemyKeys(List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (EnemyDefinition d : EnemyDefinitionManager.values()) {
            if (!seen.add(d.key))
                errors.add("[Enemy] duplicate key='" + d.key + "' (id=" + d.id + ") in cache/enemies/" + d.key + ".json");
        }
    }

    private static void checkObjectKeys(List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (ObjectDefinition d : ObjectDefinitionManager.values()) {
            if (!seen.add(d.key))
                errors.add("[Object] duplicate key='" + d.key + "' (id=" + d.id + ") in cache/objects/" + d.key + ".json");
        }
    }

    private static void checkNpcKeys(List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (NpcDefinition d : NpcDefinitionManager.values()) {
            if (!seen.add(d.key))
                errors.add("[NPC] duplicate key='" + d.key + "' (id=" + d.id + ") in cache/npcs/" + d.key + ".json");
        }
    }

    // -----------------------------------------------------------------------
    //  Stat validation
    // -----------------------------------------------------------------------

    private static void validateItemStats(List<String> errors) {
        for (ItemDefinition item : ItemDefinitionManager.values()) {
            if (item.value < 0)
                errors.add("[Item] id=" + item.id + " ('" + item.name + "') has negative value=" + item.value);
        }
    }

    private static void validateEnemyStats(List<String> errors) {
        for (EnemyDefinition e : EnemyDefinitionManager.values()) {
            String ref = "[Enemy] '" + e.key + "' (id=" + e.id + ") in cache/enemies/" + e.key + ".json";
            if (e.maxHp <= 0)
                errors.add(ref + " — maxHp must be > 0, got " + e.maxHp);
            if (e.attack < 0)
                errors.add(ref + " — attack must be >= 0, got " + e.attack);
            if (e.defense < 0)
                errors.add(ref + " — defense must be >= 0, got " + e.defense);
            if (e.minDamage < 0)
                errors.add(ref + " — minDamage must be >= 0, got " + e.minDamage);
            if (e.maxDamage < e.minDamage)
                errors.add(ref + " — maxDamage (" + e.maxDamage + ") must be >= minDamage (" + e.minDamage + ")");
            if (e.attackCooldownMs <= 0)
                errors.add(ref + " — attackCooldownMs must be > 0, got " + e.attackCooldownMs);
            if (e.wander && e.wanderRadiusTiles <= 0)
                errors.add(ref + " — wander=true but wanderRadius is 0 or missing");
        }
    }

    private static void validateObjectStats(List<String> errors) {
        for (ObjectDefinition obj : ObjectDefinitionManager.values()) {
            String ref = "[Object] '" + obj.key + "' (id=" + obj.id + ") in cache/objects/" + obj.key + ".json";
            if (obj.xp < 0)
                errors.add(ref + " — xp must be >= 0, got " + obj.xp);
            if (obj.respawnMs < 0)
                errors.add(ref + " — respawnMs must be >= 0, got " + obj.respawnMs);
        }
    }

    // -----------------------------------------------------------------------
    //  Cross-reference validation
    // -----------------------------------------------------------------------

    private static void validateEnemyDropTables(List<String> errors) {
        for (EnemyDefinition enemy : EnemyDefinitionManager.values()) {
            for (EnemyDefinition.DropEntry drop : enemy.dropTable) {
                if (!ItemDefinitionManager.exists(drop.itemId)) {
                    errors.add("[Enemy] '" + enemy.key + "' (id=" + enemy.id
                            + ") in cache/enemies/" + enemy.key + ".json"
                            + " — drop table references unknown itemId=" + drop.itemId);
                }
                if (drop.chance < 0.0 || drop.chance > 1.0) {
                    errors.add("[Enemy] '" + enemy.key + "' (id=" + enemy.id
                            + ") drop for itemId=" + drop.itemId
                            + " has invalid chance=" + drop.chance + " (must be 0.0–1.0)");
                }
            }
        }
    }

    private static void validateObjectResources(List<String> errors) {
        for (ObjectDefinition obj : ObjectDefinitionManager.values()) {
            if (obj.resourceItemId > 0 && !ItemDefinitionManager.exists(obj.resourceItemId)) {
                errors.add("[Object] '" + obj.key + "' (id=" + obj.id
                        + ") in cache/objects/" + obj.key + ".json"
                        + " — resourceItemId=" + obj.resourceItemId + " not found in item definitions");
            }
        }
    }

    private static void validateNpcShopRefs(List<String> errors, ShopManager shopManager) {
        for (NpcDefinition npc : NpcDefinitionManager.values()) {
            if (npc.shopkeeper) {
                if (npc.shopId == null || npc.shopId.isBlank()) {
                    errors.add("[NPC] '" + npc.key + "' (id=" + npc.id
                            + ") in cache/npcs/" + npc.key + ".json"
                            + " — shopkeeper=true but shopId is missing or blank");
                } else if (!shopManager.hasShop(npc.shopId)) {
                    errors.add("[NPC] '" + npc.key + "' (id=" + npc.id
                            + ") in cache/npcs/" + npc.key + ".json"
                            + " — references unknown shopId='" + npc.shopId + "'");
                }
            }
            if (npc.wander && npc.wanderRadiusTiles <= 0) {
                errors.add("[NPC] '" + npc.key + "' (id=" + npc.id
                        + ") in cache/npcs/" + npc.key + ".json"
                        + " — wander=true but wanderRadius is 0 or missing");
            }
        }
    }
}
