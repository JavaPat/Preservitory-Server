package com.classic.preservitory.server.moderation;

import java.util.HashMap;
import java.util.Map;

public class ModerationSystem {

    private static final long MESSAGE_COOLDOWN_MS = 800;   // 0.8 seconds
    private static final int  SPAM_REPEAT_LIMIT   = 3;     // same msg 3 times
    private static final long SPAM_MUTE_MS        = 15_000; // 15 sec mute

    private static class PlayerModeration {
        int strikes = 0;
        long mutedUntil = 0;

        // 🔥 NEW
        long lastMessageTime = 0;
        String lastMessage = "";
        int spamCount = 0;
    }

    private final Map<String, PlayerModeration> players = new HashMap<>();
    private final Map<String, PlayerRole> roles = new HashMap<>();

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    public Result handleMessage(String playerId, String original, String filtered) {
        PlayerModeration data = players.computeIfAbsent(playerId, k -> new PlayerModeration());

        long now = System.currentTimeMillis();

        // 1. Check if muted
        if (data.mutedUntil > now) {
            return Result.muted(data.mutedUntil - now);
        }

        // 🔥 2. Cooldown check
        if (now - data.lastMessageTime < MESSAGE_COOLDOWN_MS) {
            return Result.blocked();//Result.warn("You're sending messages too fast.");
        }

        // 🔥 3. Spam detection (same message)
        if (filtered.equalsIgnoreCase(data.lastMessage)) {
            data.spamCount++;

            if (data.spamCount >= SPAM_REPEAT_LIMIT) {
                data.mutedUntil = now + SPAM_MUTE_MS;
                data.spamCount = 0;
                return Result.muted(SPAM_MUTE_MS);
            }
        } else {
            data.spamCount = 0;
        }

        data.lastMessage = filtered;
        data.lastMessageTime = now;

        // 4. Detect if message was censored
        boolean wasFiltered = filtered.contains("****");

        // 5. Strike decay (good behaviour)
        if (!wasFiltered && data.strikes > 0) {
            data.strikes--;
        }

        // 6. Escalation for bad language
        if (wasFiltered) {
            data.strikes++;

            if (data.strikes == 1) {
                return Result.warn("Please keep the chat respectful.");
            }

            if (data.strikes == 2) {
                data.mutedUntil = now + 10_000;
                return Result.muted(10_000);
            }

            if (data.strikes >= 3) {
                data.mutedUntil = now + 30_000;
                return Result.muted(30_000);
            }
        }

        return Result.allowed();
    }

    // -----------------------------------------------------------------------
    //  Result type
    // -----------------------------------------------------------------------

    public static class Result {
        public final boolean allowed;
        public final boolean muted;
        public final long muteTime;
        public final String message;

        private Result(boolean allowed, boolean muted, long muteTime, String message) {
            this.allowed = allowed;
            this.muted = muted;
            this.muteTime = muteTime;
            this.message = message;
        }

        public static Result allowed() {
            return new Result(true, false, 0, null);
        }

        public static Result warn(String msg) {
            return new Result(true, false, 0, msg);
        }

        public static Result muted(long ms) {
            return new Result(false, true, ms, "You are muted for " + (ms / 1000) + " seconds.");
        }

        public static Result blocked() {
            return new Result(false, false, 0, null);
        }
    }

    public void mutePlayer(String targetId, long durationMs) {
        PlayerModeration data = players.computeIfAbsent(targetId, k -> new PlayerModeration());
        data.mutedUntil = System.currentTimeMillis() + durationMs;
    }

    public void unmutePlayer(String targetId) {
        PlayerModeration data = players.computeIfAbsent(targetId, k -> new PlayerModeration());
        data.mutedUntil = 0;
    }

    public void setRole(String playerId, PlayerRole role) {
        roles.put(playerId, role);
    }

    public PlayerRole getRole(String playerId) {
        return roles.getOrDefault(playerId, PlayerRole.PLAYER);
    }

    public boolean isMod(String playerId) {
        PlayerRole role = getRole(playerId);
        return role == PlayerRole.MODERATOR || role == PlayerRole.ADMIN || role == PlayerRole.OWNER;
    }

}
