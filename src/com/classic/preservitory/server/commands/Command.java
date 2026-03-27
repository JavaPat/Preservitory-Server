package com.classic.preservitory.server.commands;

import com.classic.preservitory.server.moderation.PlayerRole;

public interface Command {
    String getName();
    PlayerRole getRequiredRole();
    void execute(String senderId, String[] args);
}