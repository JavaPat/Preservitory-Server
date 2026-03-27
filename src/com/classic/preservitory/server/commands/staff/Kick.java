package com.classic.preservitory.server.commands.staff;

import com.classic.preservitory.server.GameServer;
import com.classic.preservitory.server.commands.Command;
import com.classic.preservitory.server.moderation.ModerationSystem;
import com.classic.preservitory.server.moderation.PlayerRole;
import com.classic.preservitory.server.player.PlayerSession;

public class Kick implements Command {

    private final GameServer server;
    private final ModerationSystem moderation;

    public Kick(GameServer server, ModerationSystem moderation) {
        this.server = server;
        this.moderation = moderation;
    }

    @Override
    public String getName() {
        return "kick";
    }

    @Override
    public PlayerRole getRequiredRole() {
        return PlayerRole.MODERATOR;
    }

    @Override
    public void execute(String senderId, String[] args) {

        if (args.length < 2) {
            server.sendToPlayer(senderId, "Usage: ::kick <username>");
            return;
        }

        String targetUsername = args[1].trim().toLowerCase();

        PlayerSession targetSession =
                server.getPlayerService().getSessionByUsername(targetUsername);

        if (targetSession == null) {
            server.sendToPlayer(senderId, "Player not found or offline.");
            return;
        }

        if (targetSession.id.equals(senderId)) {
            server.sendToPlayer(senderId, "You cannot kick yourself.");
            return;
        }

        server.disconnectPlayer(targetSession.id);

        server.broadcastSystem(targetSession.username + " was kicked by " + senderId);
    }
}
