package com.classic.preservitory.server.player;

/**
 * A single occupied inventory slot: item type and stack size.
 *
 * Mutable so that stackable items can have their amount updated in-place
 * without reallocating the slot.
 */
public final class ItemStack {

    public final int itemId;
    public int amount;

    public ItemStack(int itemId, int amount) {
        this.itemId  = itemId;
        this.amount  = amount;
    }
}
