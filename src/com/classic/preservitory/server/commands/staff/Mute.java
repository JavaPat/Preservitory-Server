package com.classic.preservitory.server.commands.staff;

import com.classic.preservitory.server.GameServer;
import com.classic.preservitory.server.commands.Command;
import com.classic.preservitory.server.moderation.ModerationSystem;

public class Mute implements Command {

    private final ModerationSystem moderation;
    private final GameServer server;

    public Mute(GameServer server, ModerationSystem moderation) {
        this.server = server;
        this.moderation = moderation;
    }

    @Override
    public String getName() {
        return "/mute";
    }

    @Override
    public void execute(String senderId, String[] args) {

        if (!moderation.isMod(senderId)) {
            server.sendToPlayer(senderId, "No permission.");
            return;
        }

        if (args.length < 3) {
            server.sendToPlayer(senderId, "Usage: /mute <player> <seconds>");
            return;
        }

        String target = args[1];
        int seconds;

        try {
            seconds = Integer.parseInt(args[2]);
        } catch (Exception e) {
            server.sendToPlayer(senderId, "Invalid number.");
            return;
        }

        moderation.mutePlayer(target, seconds * 1000L);

        server.sendToPlayer(senderId, "Muted " + target);
        server.sendToPlayer(target, "You have been muted.");
    }
}