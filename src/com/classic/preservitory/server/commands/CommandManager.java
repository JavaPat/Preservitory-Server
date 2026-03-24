package com.classic.preservitory.server.commands;

import java.util.HashMap;
import java.util.Map;

public class CommandManager {

    private final Map<String, Command> commands = new HashMap<>();

    public void register(Command cmd) {
        commands.put(cmd.getName(), cmd);
    }

    public boolean handle(String sender, String message) {
        String[] parts = message.split(" ");
        String name = parts[0].toLowerCase();

        Command cmd = commands.get(name);
        if (cmd == null) return false;

        cmd.execute(sender, parts);
        return true;
    }
}