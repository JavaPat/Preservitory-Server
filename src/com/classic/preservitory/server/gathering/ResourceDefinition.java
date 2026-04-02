package com.classic.preservitory.server.gathering;

public final class ResourceDefinition {

    public final int objectId;
    public final int levelRequired;
    public final int experience;
    public final double successChance;
    public final double depletionChance;
    public final long respawnTime;
    public final int rewardItemId;
    public final SkillType skillType;

    public ResourceDefinition(int objectId,
                              int levelRequired,
                              int experience,
                              double successChance,
                              double depletionChance,
                              long respawnTime,
                              int rewardItemId,
                              SkillType skillType) {
        this.objectId = objectId;
        this.levelRequired = levelRequired;
        this.experience = experience;
        this.successChance = successChance;
        this.depletionChance = depletionChance;
        this.respawnTime = respawnTime;
        this.rewardItemId = rewardItemId;
        this.skillType = skillType;
    }
}
