package com.classic.preservitory.server.combat;

import com.classic.preservitory.server.Constants;
import com.classic.preservitory.server.definitions.EnemyDefinition;
import com.classic.preservitory.server.definitions.EnemyDefinitionManager;
import com.classic.preservitory.server.definitions.ItemDefinition;
import com.classic.preservitory.server.definitions.ItemDefinitionManager;
import com.classic.preservitory.server.net.BroadcastService;
import com.classic.preservitory.server.net.ClientHandler;
import com.classic.preservitory.server.objects.EnemyData;
import com.classic.preservitory.server.objects.EnemyState;
import com.classic.preservitory.server.objects.LootData;
import com.classic.preservitory.server.player.ActionType;
import com.classic.preservitory.server.player.CombatStyle;
import com.classic.preservitory.server.player.PlayerSession;
import com.classic.preservitory.server.player.skills.Skill;
import com.classic.preservitory.server.player.skills.SkillService;
import com.classic.preservitory.util.ValidationUtil;
import com.classic.preservitory.server.quest.QuestService;
import com.classic.preservitory.server.world.EnemyManager;
import com.classic.preservitory.server.world.LootManager;
import com.classic.preservitory.server.world.TreeManager;

import java.util.List;
import java.util.Map;

public class CombatServices {

    private static final long ATTACK_COOLDOWN_MS = 500L;

    private static final double ATTACK_RANGE_PX = TreeManager.TILE_SIZE * 1.7;
    private static final double ATTACK_RANGE_SQ = ATTACK_RANGE_PX * ATTACK_RANGE_PX;

    private final Map<String, PlayerSession> sessions;
    private final EnemyManager enemyManager;
    private final LootManager lootManager;
    private final BroadcastService broadcastService;
    private final QuestService questService;

    public CombatServices(Map<String, PlayerSession> sessions,
                          EnemyManager enemyManager,
                          LootManager lootManager,
                          BroadcastService broadcastService,
                          QuestService questService) {
        this.sessions = sessions;
        this.enemyManager = enemyManager;
        this.lootManager = lootManager;
        this.broadcastService = broadcastService;
        this.questService = questService;
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

        EnemyDefinition enemyDef = EnemyDefinitionManager.get(enemy.definitionId);
        int damage = rollPlayerDamage(attacker, enemyDef);

        boolean killed = enemyManager.damageEnemy(enemyId, damage);

        enemyManager.engagePlayer(enemyId, attackerId);

        // ---------------- XP + HP scaling ----------------
        if (damage > 0) {
            int xp = damage * 2;

            int oldMax = attacker.getMaxHp();

            // Style-based XP with level-up detection
            Skill activeSkill = switch (attacker.combatStyle) {
                case ACCURATE   -> Skill.ATTACK;
                case AGGRESSIVE -> Skill.STRENGTH;
                case DEFENSIVE  -> Skill.DEFENCE;
            };
            int oldSkillLevel = attacker.skills.getLevel(activeSkill);
            attacker.skills.addXp(activeSkill, xp);
            int newSkillLevel = attacker.skills.getLevel(activeSkill);
            broadcastService.sendToPlayer(attacker.id,
                    "SKILL_XP " + activeSkill.name().toLowerCase() + " " + xp);
            if (newSkillLevel > oldSkillLevel) {
                broadcastService.sendToPlayer(attacker.id,
                        "SYSTEM Level up! " + activeSkill.name() + " is now level " + newSkillLevel + ".");
            }

            // Hitpoints XP with level-up detection
            int oldHpLevel = attacker.skills.getLevel(Skill.HITPOINTS);
            attacker.skills.addXp(Skill.HITPOINTS, xp / 2);
            int newHpLevel = attacker.skills.getLevel(Skill.HITPOINTS);
            if (newHpLevel > oldHpLevel) {
                broadcastService.sendToPlayer(attacker.id,
                        "SYSTEM Level up! HITPOINTS is now level " + newHpLevel + ".");
            }

            int newMax = attacker.getMaxHp();

            // Increase current HP if max HP increased
            if (newMax > oldMax) {
                attacker.heal(newMax - oldMax);
            }

            // Send updated skills
            broadcastService.sendToPlayer(attacker.id,
                    SkillService.buildSkillsPacket(attacker));

            broadcastService.broadcastAll("DAMAGE " + enemyX + " " + enemyY + " " + damage);
        }

        if (!killed && damage <= 0) return;

        broadcastService.broadcastAll(enemyManager.buildSnapshot());

        if (killed) {
            System.out.println("[CombatServices] Enemy killed by player " + attackerId + ": " + enemyId);

            questService.checkAndAdvanceKillObjective(attacker, enemy.definitionId);

            List<LootData> drops = lootManager.spawnDrops(enemyX, enemyY, enemyDef, attackerId);
            for (LootData d : drops) {
                broadcastService.sendToPlayer(attackerId, LootManager.buildAddMessage(d));
                ItemDefinition itemDef = ItemDefinitionManager.get(d.itemId);
                String itemName = (itemDef != null) ? itemDef.name : "item #" + d.itemId;
                broadcastService.sendToPlayer(attackerId, "SYSTEM Loot: " + d.count + "x " + itemName);
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

        broadcastService.sendToPlayer(playerId,
                "PLAYER_HP " + currentHp + " " + session.getMaxHp());

        System.out.println("[CombatServices] Enemy hit player " + playerId
                + " for " + reducedDamage + " (raw: " + damage + ")");
    }

    // -----------------------------------------------------------------------
    //  Damage calculation
    // -----------------------------------------------------------------------

    private int rollPlayerDamage(PlayerSession player, EnemyDefinition enemyDef) {

        int attack   = player.skills.getLevel(Skill.ATTACK)   + player.equipment.getTotalAttackBonus();
        int strength = player.skills.getLevel(Skill.STRENGTH) + player.equipment.getTotalStrengthBonus();
        int enemyDefense = enemyDef.defense;

        double hitChance = (double) attack / (attack + enemyDefense);

        if (player.combatStyle == CombatStyle.ACCURATE) {
            hitChance += 0.10;
        }

        // cap hit chance
        hitChance = Math.min(0.95, hitChance);

        if (Math.random() > hitChance) {
            return 1;   // glancing blow — always deal minimum damage
        }

        int maxHit = strength;

        if (player.combatStyle == CombatStyle.AGGRESSIVE) {
            maxHit += 2;
        }

        return 1 + (int) (Math.random() * maxHit);
    }

    private int applyDefenceReduction(PlayerSession player, int damage) {

        int defence = player.skills.getLevel(Skill.DEFENCE);

        if (player.combatStyle == CombatStyle.DEFENSIVE) {
            defence += (int)(defence * 0.1); // +10%
        }

        return Math.max(0, damage - (defence / 5));
    }

    public void handleRespawns() {
        long now = System.currentTimeMillis();

        for (PlayerSession s : sessions.values()) {
            if (!s.loggedIn) continue;

            if (s.hp <= 0) {

                // mark death time once
                if (s.deathTime == 0) {
                    s.deathTime = now;

                    broadcastService.sendToPlayer(s.id,
                            "SYSTEM You have died.");

                    continue;
                }

                // wait before respawn
                if (now - s.deathTime >= 3000) {

                    System.out.println("[CombatServices] Player respawned: " + s.username);

                    s.hp = s.getMaxHp();
                    s.x = Constants.DEFAULT_SPAWN_X;
                    s.y = Constants.DEFAULT_SPAWN_Y;
                    s.deathTime = 0;

                    // Reset movement timestamp BEFORE sending any messages.
                    // If lastMoveAtMs is stale (player was dead for seconds), the
                    // movement validator allows a large jump — the client could send
                    // a MOVE to the death position and override the respawn coords.
                    // Resetting here tightens the allowed distance immediately.
                    s.lastMoveAtMs = System.currentTimeMillis();

                    // Grant brief spawn protection so the player isn't instantly
                    // hit again the moment they respawn.
                    s.protectedUntilMs = s.lastMoveAtMs + 5_000L;

                    ClientHandler h = s.getHandler();
                    if (h != null) {
                        h.send("PLAYER_HP " + s.hp + " " + s.getMaxHp());
                        h.send("PLAYER_TELEPORT " + s.x + " " + s.y);
                    }

                    broadcastService.broadcastPositions();

                    broadcastService.sendToPlayer(s.id,
                            "SYSTEM You have respawned.");
                }
            }
        }
    }

}
