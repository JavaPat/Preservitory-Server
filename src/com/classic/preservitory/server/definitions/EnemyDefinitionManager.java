package com.classic.preservitory.server.definitions;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Runtime registry of all {@link EnemyDefinition}s. Loaded once at server startup. */
public final class EnemyDefinitionManager {

    private static Map<Integer, EnemyDefinition> registry = Map.of();
    private static Map<String,  EnemyDefinition> byKey    = Map.of();

    private EnemyDefinitionManager() {}

    public static void load(Map<Integer, EnemyDefinition> defs) {
        registry = Map.copyOf(defs);
        Map<String, EnemyDefinition> keys = new LinkedHashMap<>();
        for (EnemyDefinition d : defs.values()) keys.put(d.key, d);
        byKey = Map.copyOf(keys);
        System.out.println("[EnemyDefinitionManager] Loaded " + registry.size() + " enemy definitions.");
    }

    /**
     * Look up by numeric ID.
     *
     * @throws IllegalStateException if no definition exists for this id.
     */
    public static EnemyDefinition get(int id) {
        EnemyDefinition def = registry.get(id);
        if (def == null) {
            throw new IllegalStateException(
                    "EnemyDefinition not found for id=" + id
                    + ". Check cache/enemies/ and DefinitionValidator output.");
        }
        return def;
    }

    /** Look up by string key (e.g. "goblin"). Returns {@code null} if not found. */
    public static EnemyDefinition getByKey(String key) {
        return byKey.get(key);
    }

    public static boolean exists(int id) {
        return registry.containsKey(id);
    }

    public static Collection<EnemyDefinition> values() {
        return Collections.unmodifiableCollection(registry.values());
    }
}
