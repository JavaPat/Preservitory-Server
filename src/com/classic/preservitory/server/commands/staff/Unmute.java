package com.classic.preservitory.server.commands.staff;

import com.classic.preservitory.server.GameServer;
import com.classic.preservitory.server.commands.Command;
import com.classic.preservitory.server.moderation.ModerationSystem;
import com.classic.preservitory.server.moderation.PlayerRole;

public class Unmute implements Command {

    private final GameServer server;
    private final ModerationSystem moderation;

    public Unmute(GameServer server, ModerationSystem moderation) {
        this.server = server;
        this.moderation = moderation;
    }

    @Override
    public String getName() {
        return "unmute";
    }

    @Override
    public PlayerRole getRequiredRole() {
        return PlayerRole.MODERATOR;
    }

    @Override
    public void execute(String senderId, String[] args) {

        if (args.length < 2) {
            server.sendToPlayer(senderId, "Usage: ::unmute <username>");
            return;
        }

        String targetUsername = args[1].trim().toLowerCase();

        moderation.unmutePlayer(targetUsername);

        var targetSession = server.getPlayerService()
                .getSessionByUsername(targetUsername);

        if (targetSession != null) {
            server.sendToPlayer(targetSession.id, "You are no longer muted.");
        }

        server.sendToPlayer(senderId, "Unmuted " + targetUsername + ".");
    }

}