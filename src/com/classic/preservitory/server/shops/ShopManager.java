package com.classic.preservitory.server.shops;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of all shops.
 *
 * Shops are registered at startup (via {@link ShopDefinitionLoader}).
 * NPCs hold only a shopId string and look up the shop here at runtime.
 */
public final class ShopManager {

    private final ConcurrentHashMap<String, Shop> registry = new ConcurrentHashMap<>();

    public void registerShop(String id, String name, List<ShopItem> items,
                              Map<Integer, Integer> sellPrices) {
        registry.put(id, new Shop(id, name, items, sellPrices));
    }

    /** Return the shop for {@code shopId}, or {@code null} if not registered. */
    public Shop getShop(String shopId) {
        return registry.get(shopId);
    }

    public boolean hasShop(String shopId) {
        return registry.containsKey(shopId);
    }
}
