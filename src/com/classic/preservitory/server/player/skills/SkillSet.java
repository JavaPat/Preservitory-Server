package com.classic.preservitory.server.player.skills;

import java.util.EnumMap;

public class SkillSet {

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
        levels.put(skill, level);
    }

    public void setXp(Skill skill, int value) {
        xp.put(skill, value);
    }

    public int getLevel(Skill skill) {
        return levels.getOrDefault(skill, 1);
    }

    public int getXp(Skill skill) {
        return xp.getOrDefault(skill, 0);
    }

    public void addXp(Skill skill, int amount) {
        int newXp = getXp(skill) + amount;
        xp.put(skill, newXp);
        int newLevel = 1 + (newXp / 100);
        levels.put(skill, newLevel);
    }

    public EnumMap<Skill, Integer> getLevels() {
        return levels;
    }

    public EnumMap<Skill, Integer> getXpMap() {
        return xp;
    }
}
