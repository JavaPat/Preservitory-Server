package com.classic.preservitory.server.commands.staff;

import com.classic.preservitory.server.GameServer;
import com.classic.preservitory.server.commands.Command;
import com.classic.preservitory.server.moderation.ModerationSystem;

public class Unmute implements Command {

    private final GameServer server;
    private final ModerationSystem moderation;

    public Unmute(GameServer server, ModerationSystem moderation) {
        this.server = server;
        this.moderation = moderation;
    }

    @Override
    public String getName() {
        return "/unmute";
    }

    @Override
    public void execute(String senderId, String[] args) {

        if (!moderation.isMod(senderId)) {
            server.sendToPlayer(senderId, "No permission.");
            return;
        }

        if (args.length < 2) {
            server.sendToPlayer(senderId, "Usage: /unmute <player>");
            return;
        }

        String target = args[1];

        moderation.unmutePlayer(target);

        server.sendToPlayer(senderId, "Unmuted " + target);
        server.sendToPlayer(target, "You are no longer muted.");
    }
}