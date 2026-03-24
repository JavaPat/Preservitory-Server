package com.classic.preservitory.server.combat;

import com.classic.preservitory.server.net.BroadcastService;
import com.classic.preservitory.server.objects.EnemyData;
import com.classic.preservitory.server.objects.EnemyState;
import com.classic.preservitory.server.objects.LootData;
import com.classic.preservitory.server.player.ActionType;
import com.classic.preservitory.server.player.PlayerSession;
import com.classic.preservitory.server.player.skills.Skill;
import com.classic.preservitory.server.player.skills.SkillService;
import com.classic.preservitory.server.util.ValidationUtil;
import com.classic.preservitory.server.world.EnemyManager;
import com.classic.preservitory.server.world.LootManager;
import com.classic.preservitory.server.world.TreeManager;

import java.util.List;
import java.util.Map;

public class CombatServices {

    private static final int GOBLIN_DEFENCE_LEVEL = 2;
    private static final long ATTACK_COOLDOWN_MS = 500L;

    private static final double ATTACK_RANGE_PX = TreeManager.TILE_SIZE * 1.7;
    private static final double ATTACK_RANGE_SQ = ATTACK_RANGE_PX * ATTACK_RANGE_PX;

    private final Map<String, PlayerSession> sessions;
    private final EnemyManager enemyManager;
    private final LootManager lootManager;
    private final BroadcastService broadcastService;

    public CombatServices(Map<String, PlayerSession> sessions,
                          EnemyManager enemyManager,
                          LootManager lootManager,
                          BroadcastService broadcastService) {
        this.sessions = sessions;
        this.enemyManager = enemyManager;
        this.lootManager = lootManager;
        this.broadcastService = broadcastService;
    }

    // -----------------------------------------------------------------------
    //  Player attacks enemy
    // -----------------------------------------------------------------------

    public void handleAttack(String attackerId, String enemyId) {
        if (!ValidationUtil.isValidObjectId(enemyId)) return;

        PlayerSession attacker = sessions.get(attackerId);
        if (attacker == null || !attacker.loggedIn || !attacker.isAlive()) return;

        if (!ValidationUtil.consumeCooldown(attacker, ActionType.ATTACK, ATTACK_COOLDOWN_MS)) return;

        EnemyData enemy = enemyManager.getEnemy(enemyId);
        if (enemy == null) return;

        int enemyX;
        int enemyY;

        synchronized (enemy) {
            if (enemy.state == EnemyState.DEAD || enemy.hp <= 0) return;
            enemyX = (int) enemy.x;
            enemyY = (int) enemy.y;
        }

        if (!ValidationUtil.isWithinRange(attacker.x, attacker.y, enemyX, enemyY, ATTACK_RANGE_SQ)) return;

        int damage = rollPlayerDamage(attacker);

        if (damage > 0) {
            int xp = damage * 2;

            switch (attacker.combatStyle) {

                case ACCURATE -> attacker.skills.addXp(Skill.ATTACK, xp);

                case AGGRESSIVE -> attacker.skills.addXp(Skill.STRENGTH, xp);

                case DEFENSIVE -> attacker.skills.addXp(Skill.DEFENCE, xp);
            }

            broadcastService.sendToPlayer(attacker.id,
                    SkillService.buildSkillsPacket(attacker));
        }

        boolean killed = enemyManager.damageEnemy(enemyId, damage);

        enemyManager.engagePlayer(enemyId, attackerId);

        if (damage <= 0 && !killed) return;

        broadcastService.broadcastAll(enemyManager.buildSnapshot());

        if (killed) {
            System.out.println("[Server] Enemy killed by player: " + enemyId);

            List<LootData> drops = lootManager.spawnGoblinDrops(enemyX, enemyY);
            for (LootData d : drops) {
                broadcastService.broadcastAll(LootManager.buildAddMessage(d));
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Enemy attacks player
    // -----------------------------------------------------------------------

    public void damagePlayer(String playerId, int damage) {
        PlayerSession session = sessions.get(playerId);

        if (session == null || !session.loggedIn || !session.isAlive()) return;
        if (session.isSpawnProtected()) return;

        int reducedDamage = applyDefenceReduction(session, damage);

        int currentHp;

        synchronized (session) {
            session.hp -= reducedDamage;
            session.clampHp();
            currentHp = session.hp;
        }

        broadcastService.sendToPlayer(playerId, "PLAYER_HP " + currentHp);

        System.out.println("[Server] Enemy hit player " + playerId
                + " for " + reducedDamage + " (raw: " + damage + ")");
    }

    // -----------------------------------------------------------------------
    //  Damage calculation (USES SKILLS)
    // -----------------------------------------------------------------------

    private int rollPlayerDamage(PlayerSession player) {

        int attack = player.skills.getLevel(Skill.ATTACK);
        int strength = player.skills.getLevel(Skill.STRENGTH);

        double hitChance = (double) attack / (attack + GOBLIN_DEFENCE_LEVEL);

        if (Math.random() > hitChance) {
            return 0;
        }

        return 1 + (int) (Math.random() * strength);
    }

    private int applyDefenceReduction(PlayerSession player, int damage) {
        int defence = player.skills.getLevel(Skill.DEFENCE);
        return Math.max(0, damage - (defence / 5));
    }
}