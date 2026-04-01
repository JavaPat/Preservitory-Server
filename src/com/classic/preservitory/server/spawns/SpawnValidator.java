package com.classic.preservitory.server.spawns;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates spawn data after all files have been loaded.
 *
 * <p>Checks that spawn IDs are unique across ALL spawn files, not just within
 * each file.  Cross-file duplicates would cause entity tracking bugs at runtime
 * (two entities with the same ID in different managers would be silently masked).
 */
public final class SpawnValidator {

    private SpawnValidator() {}

    /**
     * Throws {@link IllegalStateException} if any spawn ID appears in more than
     * one of the provided lists.
     *
     * @param lists varargs of spawn lists loaded from different files
     */
    @SafeVarargs
    public static void validateUniqueIds(List<SpawnEntry>... lists) {
        Set<String> seen   = new HashSet<>();
        List<String> dupes = new ArrayList<>();

        for (List<SpawnEntry> list : lists) {
            for (SpawnEntry entry : list) {
                if (!seen.add(entry.id)) {
                    dupes.add(entry.id);
                }
            }
        }

        if (!dupes.isEmpty()) {
            throw new IllegalStateException(
                    "[SpawnValidator] Duplicate spawn IDs found across spawn files: " + dupes
                    + ". Each spawn ID must be unique across npcs.json, enemies.json, and objects.json.");
        }

        System.out.println("[SpawnValidator] All spawn IDs unique across " + seen.size() + " entries.");
    }
}
