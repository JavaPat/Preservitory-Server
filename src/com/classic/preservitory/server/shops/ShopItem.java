package com.classic.preservitory.server.shops;

/**
 * One item offered for sale in a shop.
 * Immutable — constructed once at server startup from shop definitions.
 */
public final class ShopItem {

    /** References an {@link com.classic.preservitory.server.definitions.ItemDefinition}. */
    public final int itemId;

    /** Price in coins the player pays to buy this item. */
    public final int price;

    /** Available stock. -1 means unlimited. */
    public final int stock;

    public ShopItem(int itemId, int price, int stock) {
        this.itemId = itemId;
        this.price  = price;
        this.stock  = stock;
    }
}
