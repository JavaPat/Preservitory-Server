package com.classic.preservitory.server.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShopSystem {

    public static class ShopEntry {
        public final String name;
        public final boolean stackable;
        public final int buyPrice;

        ShopEntry(String name, boolean stackable, int buyPrice) {
            this.name = name;
            this.stackable = stackable;
            this.buyPrice = buyPrice;
        }
    }

    private final List<ShopEntry> stock = new ArrayList<>();
    private final Map<String, Integer> sellPrices = new LinkedHashMap<>();

    public ShopSystem() {
        this(new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    public ShopSystem(Map<String, Integer> stockPrices, Map<String, Integer> sellPrices) {
        for (Map.Entry<String, Integer> entry : stockPrices.entrySet()) {
            stock.add(new ShopEntry(entry.getKey(), true, entry.getValue()));
        }
        this.sellPrices.putAll(sellPrices);
        if (!stock.isEmpty() || !this.sellPrices.isEmpty()) {
            return;
        }

        stock.add(new ShopEntry("Candle", true, 5));
        stock.add(new ShopEntry("Rope", true, 10));
        stock.add(new ShopEntry("Gem", true, 100));
        this.sellPrices.put("Logs", 2);
        this.sellPrices.put("Ore", 3);
    }

    public String buyItem(String name, PlayerInventory inventory) {
        ShopEntry entry = findEntry(name);
        if (entry == null) return "Item not sold here.";
        if (inventory == null) return "Inventory unavailable.";

        if (!inventory.removeItem("Coins", entry.buyPrice)) {
            return "Not enough Coins.";
        }

        inventory.addItem(entry.name, 1);
        return null;
    }

    public String sellItem(String name, PlayerInventory inventory) {
        if (inventory == null) return "Inventory unavailable.";

        Integer sellPrice = sellPrices.get(name);
        if (sellPrice == null) return "That item cannot be sold here.";
        if (!inventory.removeItem(name, 1)) return "You do not have that item.";

        inventory.addItem("Coins", sellPrice);
        return null;
    }

    public List<ShopEntry> getStock() {
        return Collections.unmodifiableList(stock);
    }

    public Map<String, Integer> getSellPrices() {
        return Collections.unmodifiableMap(sellPrices);
    }

    public String buildSnapshot() {
        return "SHOP\t" + encode(stock) + "\t" + encode(sellPrices);
    }

    private String encode(List<ShopEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (ShopEntry entry : entries) {
            if (sb.length() > 0) sb.append(';');
            sb.append(entry.name).append(':').append(entry.buyPrice);
        }
        return sb.toString();
    }

    private String encode(Map<String, Integer> prices) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : prices.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(entry.getKey()).append(':').append(entry.getValue());
        }
        return sb.toString();
    }

    private ShopEntry findEntry(String name) {
        for (ShopEntry entry : stock) {
            if (entry.name.equals(name)) {
                return entry;
            }
        }
        return null;
    }
}
