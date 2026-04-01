package com.classic.preservitory.server.player;

import com.classic.preservitory.server.definitions.ItemDefinitionManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Slot-based player inventory — 28 slots (OSRS-style).
 *
 * <p>Pure data and logic only: no networking, no UI, no side effects.</p>
 *
 * <ul>
 *   <li>Stackable items merge into a single slot regardless of count.</li>
 *   <li>Non-stackable items each occupy one slot.</li>
 *   <li>Stackability is resolved via {@link ItemDefinitionManager}; unknown
 *       items default to non-stackable.</li>
 * </ul>
 *
 * <p>Wire format produced by {@link #buildSnapshot()}: {@code INVENTORY_UPDATE slot:itemId:amount ...}</p>
 */
public final class Inventory {

    public static final int SIZE = 28;

    private final ItemStack[] slots = new ItemStack[SIZE];

    // -----------------------------------------------------------------------
    //  Core operations
    // -----------------------------------------------------------------------

    /**
     * Add {@code amount} of {@code itemId} to the inventory.
     *
     * <p>Stackable items are merged into an existing stack, or placed in the
     * first free slot.  Non-stackable items each require a free slot and the
     * operation is all-or-nothing: if there is not enough space for every
     * unit, nothing is added and {@code false} is returned.</p>
     *
     * @return {@code true} if all items were added, {@code false} if there
     *         was insufficient space and nothing was changed.
     */
    public boolean addItem(int itemId, int amount) {
        if (itemId <= 0 || amount <= 0) return false;

        boolean stackable = isStackable(itemId);
        System.out.println("[Inventory] addItem id=" + itemId
                + " amount=" + amount + " stackable=" + stackable);

        if (stackable) {
            for (int i = 0; i < SIZE; i++) {
                if (slots[i] != null && slots[i].itemId == itemId) {
                    long total = (long) slots[i].amount + amount;
                    slots[i].amount = (int) Math.min(Integer.MAX_VALUE, total);
                    return true;
                }
            }
            int free = firstFreeSlot();
            if (free == -1) return false;
            slots[free] = new ItemStack(itemId, amount);
            return true;
        }

        // Non-stackable — need exactly `amount` free slots (all-or-nothing)
        if (getFreeSlots() < amount) return false;
        for (int i = 0; i < SIZE && amount > 0; i++) {
            if (slots[i] == null) {
                slots[i] = new ItemStack(itemId, 1);
                amount--;
            }
        }
        return true;
    }

    /**
     * Remove {@code amount} units of {@code itemId}.
     *
     * @return {@code true} if the items were removed; {@code false} if the
     *         player did not have enough and nothing was changed.
     */
    public boolean removeItem(int itemId, int amount) {
        if (itemId <= 0 || amount <= 0) return false;
        if (countOf(itemId) < amount) return false;

        for (int i = 0; i < SIZE && amount > 0; i++) {
            if (slots[i] == null || slots[i].itemId != itemId) continue;
            int take = Math.min(amount, slots[i].amount);
            slots[i].amount -= take;
            amount -= take;
            if (slots[i].amount == 0) slots[i] = null;
        }
        return true;
    }

    // -----------------------------------------------------------------------
    //  Queries
    // -----------------------------------------------------------------------

    /** Total count of {@code itemId} across all slots. */
    public int countOf(int itemId) {
        int total = 0;
        for (ItemStack slot : slots) {
            if (slot != null && slot.itemId == itemId) total += slot.amount;
        }
        return total;
    }

    /**
     * Returns {@code true} if at least one unit of {@code itemId} can be added.
     *
     * <p>For stackable items this is true whenever an existing stack exists or
     * any slot is free.  For non-stackable items at least one free slot is
     * required.</p>
     */
    public boolean hasSpace(int itemId) {
        return hasSpace(itemId, 1);
    }

    /**
     * Returns {@code true} if {@code amount} units of {@code itemId} can be
     * added without exceeding the 28-slot limit.
     */
    public boolean hasSpace(int itemId, int amount) {
        if (amount <= 0) return true;
        if (isStackable(itemId)) {
            for (ItemStack slot : slots) {
                if (slot != null && slot.itemId == itemId) return true; // existing stack
            }
            return getFreeSlots() > 0;
        }
        return getFreeSlots() >= amount;
    }

    /** Number of currently empty slots (0–28). */
    public int getFreeSlots() {
        int free = 0;
        for (ItemStack slot : slots) {
            if (slot == null) free++;
        }
        return free;
    }

    // -----------------------------------------------------------------------
    //  Slot-level access
    // -----------------------------------------------------------------------

    /**
     * Returns a defensive copy of the internal slot array, indexed by slot position.
     * A {@code null} entry indicates an empty slot.
     *
     * <p>Intended for read-only consumers such as UI rendering and serialisation.
     * To mutate individual slots use {@link #setSlot}, {@link #clearSlot}, or
     * {@link #swapSlots}.</p>
     */
    public ItemStack[] getSlots() {
        return slots.clone();
    }

    /**
     * Returns the {@link ItemStack} occupying slot {@code index}, or {@code null}
     * if the slot is empty.
     *
     * @throws IndexOutOfBoundsException if {@code index} is outside [0, SIZE).
     */
    public ItemStack getSlot(int index) {
        checkBounds(index);
        return slots[index];
    }

    /**
     * Places {@code stack} directly into slot {@code index}, overwriting whatever
     * was there.  Pass {@code null} to empty the slot.
     *
     * <p>Low-level operation — intended for drag-and-drop and admin tooling.
     * Normal item flow should use {@link #addItem} / {@link #removeItem}.</p>
     *
     * @throws IndexOutOfBoundsException if {@code index} is outside [0, SIZE).
     */
    public void setSlot(int index, ItemStack stack) {
        checkBounds(index);
        slots[index] = stack;
    }

    /**
     * Empties slot {@code index}.
     *
     * @throws IndexOutOfBoundsException if {@code index} is outside [0, SIZE).
     */
    public void clearSlot(int index) {
        checkBounds(index);
        slots[index] = null;
    }

    /**
     * Swaps the {@link ItemStack} references at slots {@code a} and {@code b}.
     * Works correctly when {@code a == b} (no-op).
     *
     * @throws IndexOutOfBoundsException if either index is outside [0, SIZE).
     */
    public void swapSlots(int a, int b) {
        checkBounds(a);
        checkBounds(b);
        ItemStack tmp = slots[a];
        slots[a] = slots[b];
        slots[b] = tmp;
    }

    // -----------------------------------------------------------------------
    //  Lifecycle
    // -----------------------------------------------------------------------

    public void clear() {
        Arrays.fill(slots, null);
    }

    // -----------------------------------------------------------------------
    //  Serialisation helpers
    // -----------------------------------------------------------------------

    /**
     * Returns a collapsed view of items as {@code itemId → total count}.
     * Non-stackable items with multiple stacks are summed together.
     * Used by {@link com.classic.preservitory.server.player.PlayerService} to
     * persist inventory to {@link PlayerData}.
     */
    public Map<Integer, Integer> getItems() {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (ItemStack slot : slots) {
            if (slot != null) result.merge(slot.itemId, slot.amount, Integer::sum);
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Builds the {@code INVENTORY_UPDATE} packet sent to the client.
     *
     * <p>Format: {@code INVENTORY_UPDATE slot:itemId:amount ...} for all 28 slots.
     * Empty slots are represented as {@code slot:-1:0}.</p>
     */
    public String buildSnapshot() {
        StringBuilder sb = new StringBuilder("INVENTORY_UPDATE");
        for (int i = 0; i < SIZE; i++) {
            ItemStack s = slots[i];
            sb.append(' ').append(i).append(':');
            if (s != null) {
                sb.append(s.itemId).append(':').append(s.amount);
            } else {
                sb.append("-1:0");
            }
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    //  Private helpers
    // -----------------------------------------------------------------------

    private boolean isStackable(int itemId) {
        return ItemDefinitionManager.exists(itemId)
                && ItemDefinitionManager.get(itemId).stackable;
    }

    private int firstFreeSlot() {
        for (int i = 0; i < SIZE; i++) {
            if (slots[i] == null) return i;
        }
        return -1;
    }

    private static void checkBounds(int index) {
        if (index < 0 || index >= SIZE) {
            throw new IndexOutOfBoundsException("Slot index " + index + " out of range [0, " + SIZE + ")");
        }
    }
}
