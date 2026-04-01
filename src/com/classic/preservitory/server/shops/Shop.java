package com.classic.preservitory.server.shops;

import com.classic.preservitory.server.definitions.ItemIds;
import com.classic.preservitory.server.player.Inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side shop: holds stock and handles buy/sell transactions.
 *
 * Registered in {@link ShopManager} at startup; NPCs reference it by {@link #id}.
 *
 * Packet produced by {@link #buildSnapshot()}:
 *   SHOP \t id \t name \t buyItems \t sellItems
 *
 *   buyItems  — comma-separated  itemId:price:stock   (stock -1 = unlimited)
 *   sellItems — comma-separated  itemId:price
 */
public final class Shop {

    public final String id;
    public final String name;

    private final List<ShopItem>        stock;
    private final Map<Integer, Integer> sellPrices;   // itemId → sell price

    public Shop(String id, String name, List<ShopItem> stock, Map<Integer, Integer> sellPrices) {
        this.id         = id;
        this.name       = name;
        this.stock      = Collections.unmodifiableList(new ArrayList<>(stock));
        this.sellPrices = Collections.unmodifiableMap(new LinkedHashMap<>(sellPrices));
    }

    public List<ShopItem>       getStock()      { return stock;      }
    public Map<Integer,Integer> getSellPrices() { return sellPrices; }

    // -----------------------------------------------------------------------
    //  Transactions
    // -----------------------------------------------------------------------

    public String buyItem(int itemId, Inventory inventory) {
        ShopItem item = findItem(itemId);
        if (item == null) return "Item not sold here.";
        if (inventory == null) return "Inventory unavailable.";
        if (!inventory.hasSpace(item.itemId)) return "Your inventory is full.";
        if (!inventory.removeItem(ItemIds.COINS, item.price)) return "Not enough Coins.";
        inventory.addItem(item.itemId, 1);
        return null;
    }

    public String sellItem(int itemId, Inventory inventory) {
        if (inventory == null) return "Inventory unavailable.";
        Integer sellPrice = sellPrices.get(itemId);
        if (sellPrice == null) return "That item cannot be sold here.";
        if (!inventory.removeItem(itemId, 1)) return "You do not have that item.";
        inventory.addItem(ItemIds.COINS, sellPrice);
        return null;
    }

    // -----------------------------------------------------------------------
    //  Packet serialisation
    // -----------------------------------------------------------------------

    public String buildSnapshot() {
        return "SHOP\t" + id + "\t" + name + "\t" + encodeStock() + "\t" + encodeSell();
    }

    private String encodeStock() {
        StringBuilder sb = new StringBuilder();
        for (ShopItem item : stock) {
            if (sb.length() > 0) sb.append(',');
            sb.append(item.itemId).append(':').append(item.price).append(':').append(item.stock);
        }
        return sb.toString();
    }

    private String encodeSell() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Integer> e : sellPrices.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    private ShopItem findItem(int itemId) {
        for (ShopItem item : stock) {
            if (item.itemId == itemId) return item;
        }
        return null;
    }
}
