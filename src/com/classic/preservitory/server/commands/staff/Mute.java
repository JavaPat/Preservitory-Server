package com.classic.preservitory.server.commands.staff;

import com.classic.preservitory.server.GameServer;
import com.classic.preservitory.server.commands.Command;
import com.classic.preservitory.server.moderation.ModerationSystem;
import com.classic.preservitory.server.moderation.PlayerRole;

public class Mute implements Command {

    private final ModerationSystem moderation;
    private final GameServer server;

    public Mute(GameServer server, ModerationSystem moderation) {
        this.server = server;
        this.moderation = moderation;
    }

    @Override
    public String getName() {
        return "mute";
    }

    @Override
    public PlayerRole getRequiredRole() {
        return PlayerRole.MODERATOR;
    }

    @Override
    public void execute(String senderId, String[] args) {

        if (args.length < 3) {
            server.sendToPlayer(senderId, "Usage: ::mute <username> <seconds>");
            return;
        }

        String targetUsername = args[1].trim().toLowerCase();
        int seconds;

        try {
            seconds = Integer.parseInt(args[2]);
            if (seconds <= 0) throw new NumberFormatException();
        } catch (Exception e) {
            server.sendToPlayer(senderId, "Invalid duration.");
            return;
        }

        // ✅ Apply mute (username-based is fine here)
        moderation.mutePlayer(targetUsername, seconds * 1000L);

        // ✅ Try to find online player
        var targetSession = server.getPlayerService()
                .getSessionByUsername(targetUsername);

        if (targetSession != null) {
            server.sendToPlayer(targetSession.id, "You have been muted for " + seconds + " seconds.");
        }

        server.sendToPlayer(senderId, "Muted " + targetUsername + " for " + seconds + " seconds.");
    }
}