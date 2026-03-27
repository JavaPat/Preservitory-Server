package com.classic.preservitory.server.world;

import com.classic.preservitory.server.objects.TreeData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Saves and loads the full tree state to/from trees.json in the server's
 * working directory.
 *
 * Safe-write strategy: data is written to trees.json.tmp first, then
 * atomically renamed to trees.json, so a crash mid-write never corrupts
 * the last good save.
 *
 * All saves are dispatched to a single-thread executor so they never
 * block the game loop and never overlap each other.
 */
public class TreePersistence {

    private static final Path FILE     = Paths.get("trees.json");
    private static final Path FILE_TMP = Paths.get("trees.json.tmp");

    private final ExecutorService saveExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "tree-save");
                t.setDaemon(true);
                return t;
            });

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    /**
     * Enqueue an async save of the current tree snapshot.
     * Takes an immutable copy of the values so the live map can keep mutating.
     */
    public void saveAllTrees(Map<String, TreeData> trees) {
        List<TreeData> snapshot = new ArrayList<>(trees.values());
        saveExecutor.submit(() -> writeSnapshot(snapshot));
    }

    /**
     * Synchronously load trees from the file.
     * Returns {@code null} if the file does not exist or cannot be parsed.
     */
    public List<TreeData> loadAllTrees() {
        if (!Files.exists(FILE)) return null;

        try {
            String json = new String(Files.readAllBytes(FILE), StandardCharsets.UTF_8);
            List<TreeData> loaded = deserialize(json);
            System.out.println("[Persistence] Loaded " + loaded.size() + " trees from " + FILE);
            return loaded;
        } catch (IOException | RuntimeException e) {
            System.err.println("[Persistence] Failed to load " + FILE + ": " + e.getMessage());
            return null;
        }
    }

    // -----------------------------------------------------------------------
    //  Write
    // -----------------------------------------------------------------------

    private void writeSnapshot(List<TreeData> snapshot) {
        try {
            byte[] bytes = serialize(snapshot).getBytes(StandardCharsets.UTF_8);
            Files.write(FILE_TMP, bytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.SYNC);

            try {
                Files.move(FILE_TMP, FILE,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(FILE_TMP, FILE, StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (IOException e) {
            System.err.println("[Persistence] Save failed: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    //  Serialisation — hand-rolled to avoid requiring Gson/Jackson
    // -----------------------------------------------------------------------

    private String serialize(List<TreeData> trees) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < trees.size(); i++) {
            TreeData t = trees.get(i);
            sb.append("  {")
              .append("\"id\":\"").append(t.id).append("\",")
              .append("\"typeId\":\"").append(t.typeId).append("\",")
              .append("\"x\":").append(t.x).append(",")
              .append("\"y\":").append(t.y).append(",")
              .append("\"alive\":").append(t.alive).append(",")
              .append("\"respawnTime\":").append(t.respawnTime)
              .append('}');
            if (i < trees.size() - 1) sb.append(',');
            sb.append('\n');
        }
        return sb.append(']').toString();
    }

    // -----------------------------------------------------------------------
    //  Deserialisation
    // -----------------------------------------------------------------------

    private List<TreeData> deserialize(String json) {
        List<TreeData> result = new ArrayList<>();
        long now = System.currentTimeMillis();

        int i = 0;
        while (i < json.length()) {
            int start = json.indexOf('{', i);
            if (start == -1) break;
            int end = json.indexOf('}', start);
            if (end == -1) break;

            String obj = json.substring(start + 1, end);

            String  id          = extractString (obj, "id");
            String  typeId      = extractString (obj, "typeId");
            int     x           = extractInt    (obj, "x");
            int     y           = extractInt    (obj, "y");
            boolean alive       = extractBoolean(obj, "alive");
            long    respawnTime = extractLong   (obj, "respawnTime");

            if (id != null) {
                if (typeId == null || typeId.isBlank()) typeId = "tree";
                // If the respawn timer already expired while the server was down,
                // bring the tree back immediately.
                if (!alive && respawnTime > 0 && now >= respawnTime) {
                    alive       = true;
                    respawnTime = 0L;
                }
                result.add(new TreeData(id, typeId, x, y, alive, respawnTime));
            }

            i = end + 1;
        }

        return result;
    }

    // -----------------------------------------------------------------------
    //  Primitive extractors
    // -----------------------------------------------------------------------

    private String extractString(String obj, String key) {
        String marker = "\"" + key + "\":\"";
        int s = obj.indexOf(marker);
        if (s == -1) return null;
        s += marker.length();
        int e = obj.indexOf('"', s);
        return e == -1 ? null : obj.substring(s, e);
    }

    private int extractInt(String obj, String key) {
        long v = extractLong(obj, key);
        return (int) v;
    }

    private long extractLong(String obj, String key) {
        String marker = "\"" + key + "\":";
        int s = obj.indexOf(marker);
        if (s == -1) return 0L;
        s += marker.length();
        int e = s;
        while (e < obj.length() && (Character.isDigit(obj.charAt(e)) || obj.charAt(e) == '-')) e++;
        try { return Long.parseLong(obj.substring(s, e)); } catch (NumberFormatException ex) { return 0L; }
    }

    private boolean extractBoolean(String obj, String key) {
        String marker = "\"" + key + "\":";
        int s = obj.indexOf(marker);
        if (s == -1) return true;
        return obj.startsWith("true", s + marker.length());
    }
}
