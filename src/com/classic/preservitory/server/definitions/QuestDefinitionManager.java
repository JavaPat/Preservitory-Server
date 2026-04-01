package com.classic.preservitory.server.definitions;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Runtime registry of all {@link QuestDefinition}s. Loaded once at server startup. */
public final class QuestDefinitionManager {

    private static Map<Integer, QuestDefinition> byId  = Map.of();
    private static Map<String,  QuestDefinition> byKey = Map.of();

    private QuestDefinitionManager() {}

    public static void load(Map<Integer, QuestDefinition> defs) {
        byId = Collections.unmodifiableMap(new LinkedHashMap<>(defs));
        Map<String, QuestDefinition> km = new LinkedHashMap<>();
        for (QuestDefinition d : defs.values()) km.put(d.key, d);
        byKey = Collections.unmodifiableMap(km);
    }

    /** Returns the definition for {@code id}, or {@code null} if not found. */
    public static QuestDefinition get(int id) {
        return byId.get(id);
    }

    /** Returns the definition whose {@link QuestDefinition#key} equals {@code key}, or {@code null}. */
    public static QuestDefinition getByKey(String key) {
        return key != null ? byKey.get(key) : null;
    }

    public static boolean exists(int id) {
        return byId.containsKey(id);
    }

    public static Collection<QuestDefinition> values() {
        return byId.values();
    }
}
