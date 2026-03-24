package com.classic.preservitory.server.commands.staff;

import com.classic.preservitory.server.GameServer;
import com.classic.preservitory.server.commands.Command;
import com.classic.preservitory.server.moderation.ModerationSystem;

public class Kick implements Command {

    private final GameServer server;
    private final ModerationSystem moderation;

    public Kick(GameServer server, ModerationSystem moderation) {
        this.server = server;
        this.moderation = moderation;
    }

    @Override
    public String getName() {
        return "/kick";
    }

    @Override
    public void execute(String senderId, String[] args) {

        if (!moderation.isMod(senderId)) {
            server.sendToPlayer(senderId, "No permission.");
            return;
        }

        if (args.length < 2) {
            server.sendToPlayer(senderId, "Usage: /kick <player>");
            return;
        }

        String target = args[1].toUpperCase();

        boolean success = server.disconnectPlayer(target);

        if (!success) {
            server.sendToPlayer(senderId, "Player not found.");
            return;
        }

        if (target.equals(senderId)) {
            server.sendToPlayer(senderId, "You cannot kick yourself.");
            return;
        }

        server.broadcastSystem(target + " was kicked by " + senderId);
    }
}
