package com.classic.preservitory.server.player.skills;

import java.util.EnumMap;

public class SkillSet {

    public static final int MAX_LEVEL = 99;

    /**
     * OSRS cumulative XP thresholds.  Index = level (1–99); value = total XP needed to reach it.
     * XP_TABLE[1] = 0, XP_TABLE[2] = 83, …, XP_TABLE[99] = 13_034_431.
     */
    private static final int[] XP_TABLE = buildOsrsXpTable();

    private static int[] buildOsrsXpTable() {
        int[] table = new int[MAX_LEVEL + 1];
        table[1] = 0;
        double points = 0;
        for (int lvl = 1; lvl < MAX_LEVEL; lvl++) {
            points += Math.floor(lvl + 300.0 * Math.pow(2.0, lvl / 7.0));
            table[lvl + 1] = (int) (points / 4);
        }
        return table;
    }

    /** Returns the level (1–99) that corresponds to the given cumulative XP total. */
    public static int levelForXp(int totalXp) {
        for (int lvl = MAX_LEVEL; lvl >= 2; lvl--) {
            if (totalXp >= XP_TABLE[lvl]) return lvl;
        }
        return 1;
    }

    /** Returns the OSRS cumulative XP required to reach the given level. */
    public static int xpForLevel(int level) {
        if (level < 1)          return 0;
        if (level > MAX_LEVEL)  return XP_TABLE[MAX_LEVEL];
        return XP_TABLE[level];
    }

    private final EnumMap<Skill, Integer> levels = new EnumMap<>(Skill.class);
    private final EnumMap<Skill, Integer> xp     = new EnumMap<>(Skill.class);

    public SkillSet() {
        reset();
    }

    public void reset() {
        for (Skill skill : Skill.values()) {
            levels.put(skill, 1);
            xp.put(skill, 0);
        }
    }

    public void setLevel(Skill skill, int level) {
        levels.put(skill, Math.max(1, Math.min(MAX_LEVEL, level)));
    }

    public void setXp(Skill skill, int value) {
        xp.put(skill, Math.max(0, value));
    }

    public int getLevel(Skill skill) {
        return levels.getOrDefault(skill, 1);
    }

    public int getXp(Skill skill) {
        return xp.getOrDefault(skill, 0);
    }

    /**
     * Add XP to a skill, then recompute the level using the OSRS XP table.
     * Both XP and level are updated atomically; levels can only increase.
     */
    public void addXp(Skill skill, int amount) {
        if (amount <= 0) return;
        int newXp   = getXp(skill) + amount;
        int newLevel = Math.min(MAX_LEVEL, levelForXp(newXp));
        xp.put(skill, newXp);
        // Only update level if it increased — never overwrite a higher saved level
        if (newLevel > getLevel(skill)) {
            levels.put(skill, newLevel);
        }
    }

    public EnumMap<Skill, Integer> getLevels() {
        return levels;
    }

    public EnumMap<Skill, Integer> getXpMap() {
        return xp;
    }
}
