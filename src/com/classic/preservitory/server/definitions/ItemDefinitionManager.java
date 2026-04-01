package com.classic.preservitory.server.definitions;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/** Runtime registry of all {@link ItemDefinition}s. Loaded once at server startup. */
public final class ItemDefinitionManager {

    private static Map<Integer, ItemDefinition> registry = Map.of();

    private ItemDefinitionManager() {}

    public static void load(Map<Integer, ItemDefinition> defs) {
        registry = Map.copyOf(defs);
        System.out.println("[ItemDefinitionManager] Loaded " + registry.size() + " item definitions.");
    }

    /**
     * Return the definition for {@code id}.
     *
     * @throws IllegalStateException if no definition exists for this id.
     */
    public static ItemDefinition get(int id) {
        ItemDefinition def = registry.get(id);
        if (def == null) {
            throw new IllegalStateException(
                    "ItemDefinition not found for id=" + id
                    + ". Check cache/items/ and DefinitionValidator output.");
        }
        return def;
    }

    public static boolean exists(int id) {
        return registry.containsKey(id);
    }

    public static Collection<ItemDefinition> values() {
        return Collections.unmodifiableCollection(registry.values());
    }
}
