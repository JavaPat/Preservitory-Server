package com.classic.preservitory.server.gathering;

import com.classic.preservitory.server.player.ActionType;
import com.classic.preservitory.server.player.skills.Skill;

public enum SkillType {
    WOODCUTTING(Skill.WOODCUTTING, ActionType.CHOP),
    MINING(Skill.MINING, ActionType.MINE);

    private final Skill skill;
    private final ActionType actionType;

    SkillType(Skill skill, ActionType actionType) {
        this.skill = skill;
        this.actionType = actionType;
    }

    public Skill skill() {
        return skill;
    }

    public ActionType actionType() {
        return actionType;
    }

    public String packetName() {
        return name().toLowerCase();
    }
}
