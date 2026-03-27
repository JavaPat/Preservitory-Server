package com.classic.preservitory.server.commands;

import com.classic.preservitory.server.GameServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

public class CommandManager {

    private final GameServer server;

    public CommandManager(GameServer server) {
        this.server = server;
    }

    private final Map<String, Command> commands = new HashMap<>();

    public void register(Command cmd) {
        commands.put(cmd.getName(), cmd);
    }

    public boolean handle(String senderId, String message) {
        String[] parts = message.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isBlank()) return false;

        String name = parts[0].toLowerCase();
        Command cmd = commands.get(name);

        if (cmd == null) return false;

        var session = server.getSession(senderId);
        if (session == null) return true;

        // 🔒 Permission check
        if (session.getRights().ordinal() < cmd.getRequiredRole().ordinal()) {
            server.sendToPlayer(senderId, "You don't have permission.");
            return true;
        }

        // 🔥 ADD LOGGING HERE
        /*Files.writeString(
                Path.of("logs/commands.log"),
                "[COMMAND] " + session.username + " (" + session.getRights() + ") used: " + message + "\n",
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );*/
        //Simple logging for now.
        System.out.println("[COMMAND] "
                + session.username
                + " (" + session.getRights() + ") used: "
                + message);

        cmd.execute(senderId, parts);
        return true;
    }
}