package com.classic.preservitory.server.player.skills;

import com.classic.preservitory.server.player.PlayerSession;

public class SkillService {

    public static String buildSkillsPacket(PlayerSession session) {
        StringBuilder sb = new StringBuilder("SKILLS ");
        for (Skill skill : Skill.values()) {
            int level = session.skills.getLevel(skill);
            int xp    = session.skills.getXp(skill);
            sb.append(skill.name().toLowerCase())
              .append(':').append(level)
              .append(':').append(xp)
              .append(';');
        }
        return sb.toString();
    }
}
