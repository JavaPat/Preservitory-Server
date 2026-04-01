package com.classic.preservitory.server.woodcutting;

import com.classic.preservitory.server.definitions.ItemIds;
import com.classic.preservitory.server.player.EquipSlot;
import com.classic.preservitory.server.player.PlayerSession;

import java.util.List;

public final class AxeDefinitionManager {

    private final List<AxeDefinition> axes = List.of(
            new AxeDefinition(ItemIds.BRONZE_AXE, 1, 1.00)
    );

    public AxeDefinition getBestAvailable(PlayerSession session) {
        int woodcuttingLevel = session.skills.getLevel(com.classic.preservitory.server.player.skills.Skill.WOODCUTTING);
        int equippedWeaponId = session.equipment.getItemInSlot(EquipSlot.WEAPON);

        AxeDefinition best = null;
        for (AxeDefinition axe : axes) {
            if (woodcuttingLevel < axe.levelRequired) {
                continue;
            }
            boolean equipped = equippedWeaponId == axe.itemId;
            boolean inInventory = session.inventory.countOf(axe.itemId) > 0;
            if (!equipped && !inInventory) {
                continue;
            }
            if (best == null || axe.speedMultiplier < best.speedMultiplier) {
                best = axe;
            }
        }
        return best;
    }
}
