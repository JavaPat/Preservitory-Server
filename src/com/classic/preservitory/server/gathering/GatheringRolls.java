package com.classic.preservitory.server.gathering;

import java.util.concurrent.ThreadLocalRandom;

public final class GatheringRolls {

    private GatheringRolls() {}

    public static boolean rollSuccess(ResourceDefinition resource, int level) {
        double bonus = Math.max(0, level - resource.levelRequired) * 0.015;
        double chance = clamp(resource.successChance + bonus, 0.15, 0.95);
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    public static boolean rollDepletion(ResourceDefinition resource, int level) {
        double reduction = Math.max(0, level - resource.levelRequired) * 0.004;
        double chance = clamp(resource.depletionChance - reduction, 0.03, 0.95);
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
