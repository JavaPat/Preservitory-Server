package com.classic.preservitory.server.definitions;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Runtime registry of all {@link NpcDefinition}s. Loaded once at server startup. */
public final class NpcDefinitionManager {

    private static Map<Integer, NpcDefinition> registry = Map.of();
    private static Map<String,  NpcDefinition> byKey    = Map.of();

    private NpcDefinitionManager() {}

    public static void load(Map<Integer, NpcDefinition> defs) {
        registry = Map.copyOf(defs);
        Map<String, NpcDefinition> keys = new LinkedHashMap<>();
        for (NpcDefinition d : defs.values()) keys.put(d.key, d);
        byKey = Map.copyOf(keys);
        System.out.println("[NpcDefinitionManager] Loaded " + registry.size() + " NPC definitions.");
    }

    /**
     * Look up by numeric ID.
     *
     * @throws IllegalStateException if no definition exists for this id.
     */
    public static NpcDefinition get(int id) {
        NpcDefinition def = registry.get(id);
        if (def == null) {
            throw new IllegalStateException(
                    "NpcDefinition not found for id=" + id
                    + ". Check cache/npcs/ and DefinitionValidator output.");
        }
        return def;
    }

    /** Look up by string key (e.g. "guide"). Returns {@code null} if not found. */
    public static NpcDefinition getByKey(String key) {
        return byKey.get(key);
    }

    public static boolean exists(int id) {
        return registry.containsKey(id);
    }

    public static Collection<NpcDefinition> values() {
        return Collections.unmodifiableCollection(registry.values());
    }
}
