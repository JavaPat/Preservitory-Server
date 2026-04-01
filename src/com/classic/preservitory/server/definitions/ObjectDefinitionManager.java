package com.classic.preservitory.server.definitions;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Runtime registry of all {@link ObjectDefinition}s. Loaded once at server startup. */
public final class ObjectDefinitionManager {

    private static Map<Integer, ObjectDefinition> registry = Map.of();
    private static Map<String,  ObjectDefinition> byKey    = Map.of();

    private ObjectDefinitionManager() {}

    public static void load(Map<Integer, ObjectDefinition> defs) {
        registry = Map.copyOf(defs);
        Map<String, ObjectDefinition> keys = new LinkedHashMap<>();
        for (ObjectDefinition d : defs.values()) keys.put(d.key, d);
        byKey = Map.copyOf(keys);
        System.out.println("[ObjectDefinitionManager] Loaded " + registry.size() + " object definitions.");
    }

    /**
     * Look up by numeric ID.
     *
     * @throws IllegalStateException if no definition exists for this id.
     */
    public static ObjectDefinition get(int id) {
        ObjectDefinition def = registry.get(id);
        if (def == null) {
            throw new IllegalStateException(
                    "ObjectDefinition not found for id=" + id
                    + ". Check cache/objects/ and DefinitionValidator output.");
        }
        return def;
    }

    /** Look up by string key (e.g. "tree"). Returns {@code null} if not found. */
    public static ObjectDefinition getByKey(String key) {
        return byKey.get(key);
    }

    public static boolean exists(int id) {
        return registry.containsKey(id);
    }

    public static Collection<ObjectDefinition> values() {
        return Collections.unmodifiableCollection(registry.values());
    }
}
