package com.classic.preservitory.server.woodcutting;

import com.classic.preservitory.server.definitions.ItemIds;
import com.classic.preservitory.server.player.PlayerSession;

import java.util.List;

public final class AxeDefinitionManager {

    private final List<AxeDefinition> axes = List.of(
            new AxeDefinition(ItemIds.BRONZE_AXE, 1, 1.00)
    );

    public AxeDefinition getBestAvailable(PlayerSession session) {
        int woodcuttingLevel = session.skills.getLevel(com.classic.preservitory.server.player.skills.Skill.WOODCUTTING);

        AxeDefinition best = null;
        for (AxeDefinition axe : axes) {
            if (woodcuttingLevel < axe.levelRequired) {
                continue;
            }
            boolean hasAxe = session.inventory.countOf(axe.itemId) > 0
                    || session.equipment.containsItem(axe.itemId);
            if (!hasAxe) {
                continue;
            }
            if (best == null || axe.speedMultiplier < best.speedMultiplier) {
                best = axe;
            }
        }
        return best;
    }
}
