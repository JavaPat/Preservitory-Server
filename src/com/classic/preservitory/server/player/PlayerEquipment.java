package com.classic.preservitory.server.player;

import com.classic.preservitory.server.definitions.ItemDefinition;
import com.classic.preservitory.server.definitions.ItemDefinitionManager;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Holds the items a player currently has equipped.
 *
 * Provides aggregated combat bonuses consumed by {@link com.classic.preservitory.server.combat.CombatServices}.
 * All mutation (equip/unequip) happens server-side through PlayerService.
 */
public class PlayerEquipment {

    private final EnumMap<EquipSlot, Integer> slots = new EnumMap<>(EquipSlot.class);

    // -----------------------------------------------------------------------
    //  Mutation
    // -----------------------------------------------------------------------

    public void equip(EquipSlot slot, int itemId) {
        slots.put(slot, itemId);
    }

    public void unequip(EquipSlot slot) {
        slots.remove(slot);
    }

    public void clear() {
        slots.clear();
    }

    // -----------------------------------------------------------------------
    //  Queries
    // -----------------------------------------------------------------------

    /** @return the itemId in this slot, or {@code -1} if empty. */
    public int getItemInSlot(EquipSlot slot) {
        return slots.getOrDefault(slot, -1);
    }

    public boolean isEquipped(EquipSlot slot) {
        return slots.containsKey(slot);
    }

    public Map<EquipSlot, Integer> getSlots() {
        return Collections.unmodifiableMap(slots);
    }

    // -----------------------------------------------------------------------
    //  Aggregated bonuses
    // -----------------------------------------------------------------------

    public int getTotalAttackBonus() {
        return slots.values().stream()
                .mapToInt(id -> {
                    ItemDefinition def = ItemDefinitionManager.get(id);
                    return def.attackBonus;
                }).sum();
    }

    public int getTotalStrengthBonus() {
        return slots.values().stream()
                .mapToInt(id -> {
                    ItemDefinition def = ItemDefinitionManager.get(id);
                    return def.strengthBonus;
                }).sum();
    }

    // -----------------------------------------------------------------------
    //  Protocol
    // -----------------------------------------------------------------------

    /**
     * Builds the {@code EQUIPMENT} snapshot sent to the client.
     * Format: {@code EQUIPMENT WEAPON:1004; HELMET:1005;}
     */
    public String buildSnapshot() {
        StringBuilder sb = new StringBuilder("EQUIPMENT");
        for (Map.Entry<EquipSlot, Integer> e : slots.entrySet()) {
            sb.append(' ').append(e.getKey().name())
              .append(':').append(e.getValue())
              .append(';');
        }
        return sb.toString();
    }
}
