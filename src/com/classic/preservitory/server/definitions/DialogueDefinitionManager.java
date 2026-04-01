package com.classic.preservitory.server.definitions;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Server-side registry of {@link DialogueDefinition}s, loaded once at startup. */
public final class DialogueDefinitionManager {

    /** Keyed by dialogue id (e.g. "guide_start"). */
    private static Map<String,  DialogueDefinition> registry = Map.of();

    /** First dialogue found per npcId — used for simple non-quest NPCs. */
    private static Map<Integer, DialogueDefinition> byNpcId  = Map.of();

    private DialogueDefinitionManager() {}

    public static void load(Map<String, DialogueDefinition> defs) {
        registry = Map.copyOf(defs);

        Map<Integer, DialogueDefinition> byNpc = new LinkedHashMap<>();
        for (DialogueDefinition d : defs.values()) {
            byNpc.putIfAbsent(d.npcId, d);   // first entry per npcId wins
        }
        byNpcId = Map.copyOf(byNpc);

        System.out.println("[DialogueDefinitionManager] Loaded " + registry.size() + " dialogue definitions.");
    }

    /**
     * Returns the dialogue with the given id, or {@code null} if not found.
     * Callers that need a specific quest-state dialogue should use this.
     */
    public static DialogueDefinition get(String id) {
        return registry.get(id);
    }

    /**
     * Returns the primary (first-loaded) dialogue for the given NPC, or {@code null}.
     * Intended for simple non-quest NPCs that have exactly one dialogue.
     */
    public static DialogueDefinition getByNpcId(int npcId) {
        return byNpcId.get(npcId);
    }

    public static boolean exists(String id) {
        return registry.containsKey(id);
    }

    public static Collection<DialogueDefinition> values() {
        return Collections.unmodifiableCollection(registry.values());
    }
}
