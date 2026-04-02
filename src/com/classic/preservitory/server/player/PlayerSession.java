package com.classic.preservitory.server.player;

import com.classic.preservitory.server.dialogue.DialogueSession;
import com.classic.preservitory.server.moderation.PlayerRole;
import com.classic.preservitory.server.net.ClientHandler;
import com.classic.preservitory.server.quest.QuestProgress;
import com.classic.preservitory.server.quest.QuestState;
import com.classic.preservitory.server.player.skills.Skill;
import com.classic.preservitory.server.player.skills.SkillSet;
import com.classic.preservitory.server.world.RegionKey;
import com.classic.preservitory.server.world.TreeManager;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PlayerSession {

    public static final int PLAYER_SPAWN_X = 12 * TreeManager.TILE_SIZE;
    public static final int PLAYER_SPAWN_Y = 9 * TreeManager.TILE_SIZE;

    public final SkillSet        skills    = new SkillSet();
    public final PlayerEquipment equipment = new PlayerEquipment();
    public final Map<Integer, QuestProgress> quests = new HashMap<>();
    public final String id;
    private ClientHandler handler;

    public String username = null;
    public boolean loggedIn = false;
    public long disconnectTime = 0;
    public boolean disconnected = false;

    public final Inventory inventory = new Inventory();
    public final EnumMap<ActionType, Long> lastActionAtMs = new EnumMap<>(ActionType.class);

    public volatile int x = PLAYER_SPAWN_X, y = PLAYER_SPAWN_Y;
    public volatile int hp;
    public volatile long lastMoveAtMs = System.currentTimeMillis();
    public volatile long protectedUntilMs = 0L;
    public long deathTime = 0;

    public RegionKey currentRegion = new RegionKey(0, 0);
    public final Set<RegionKey> loadedRegions = new HashSet<>();

    public CombatStyle combatStyle = CombatStyle.ACCURATE;
    public volatile boolean shopOpen = false;
    public volatile String activeNpcId = null;
    public volatile String activeTreeId = null;
    public volatile long lastChopTime = 0L;
    public volatile PendingInteraction pendingInteraction = null;
    public PlayerData playerData;

    /** Non-null while the player is in an active dialogue session. */
    public volatile DialogueSession activeDialogue = null;

    public PlayerRole getRights() {
        return playerData != null && playerData.rights != null
                ? playerData.rights
                : PlayerRole.PLAYER;
    }

    public PlayerSession(String id, ClientHandler handler) {
        this.id = id;
        this.handler = handler;
        this.currentRegion = TreeManager.getRegionForPosition(x, y);
        this.hp = getMaxHp();
    }

    public int getMaxHp() {
        int level = Math.max(1, skills.getLevel(Skill.HITPOINTS));
        return level * 5;
    }

    public void clampHp() {
        int max = getMaxHp();
        if (hp > max) hp = max;
        if (hp < 0) hp = 0;
    }

    public void takeDamage(int damage) {
        synchronized (this) {
            hp -= Math.max(0, damage);
            clampHp();
        }
    }

    public void heal(int amount) {
        synchronized (this) {
            hp += Math.max(0, amount);
            clampHp();
        }
    }

    public boolean canSeeRegion(RegionKey region) {
        synchronized (this) {
            return loadedRegions.contains(region);
        }
    }

    public boolean isAlive() {
        return hp > 0;
    }

    public boolean isSpawnProtected() {
        return System.currentTimeMillis() < protectedUntilMs;
    }

    /** Sends the standard "inventory is full" system message directly to this player. */
    public void sendInventoryFullMessage() {
        ClientHandler h = handler;
        if (h != null) h.send("SYSTEM Your inventory is full.");
    }

    public void setHandler(ClientHandler handler) {
        this.handler = handler;
    }

    public ClientHandler getHandler() {
        return handler;
    }

}
