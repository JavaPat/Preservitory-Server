package com.classic.preservitory.server.player;

import java.util.LinkedHashMap;
import java.util.Map;

public class PlayerInventory {

    private final LinkedHashMap<String, Integer> items = new LinkedHashMap<>();

    public Map<String, Integer> getItems() {
        return items;
    }

    public void addItem(String name, int count) {
        if (name == null || name.isBlank() || count <= 0) return;
        items.merge(name, count, (current, delta) -> {
            long total = (long) current + delta;
            return (int) Math.min(Integer.MAX_VALUE, total);
        });
    }

    public void clear() {
        items.clear();
    }

    public String buildSnapshot() {
        StringBuilder sb = new StringBuilder("INVENTORY");
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            sb.append(' ').append(e.getKey())
              .append(':').append(e.getValue())
              .append(';');
        }
        return sb.toString();
    }
}
