package com.classic.preservitory.server.woodcutting;

public final class TreeDefinition {

    public final int id;
    public final int logItemId;
    public final int xp;
    public final int levelRequired;
    public final double baseSuccessChance;
    public final double depletionChance;
    public final long respawnTimeMs;

    public TreeDefinition(int id, int logItemId, int xp, int levelRequired,
                          double baseSuccessChance, double depletionChance,
                          long respawnTimeMs) {
        this.id = id;
        this.logItemId = logItemId;
        this.xp = xp;
        this.levelRequired = levelRequired;
        this.baseSuccessChance = baseSuccessChance;
        this.depletionChance = depletionChance;
        this.respawnTimeMs = respawnTimeMs;
    }
}
