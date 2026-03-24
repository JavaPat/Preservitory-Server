package com.classic.preservitory.server.commands;

public interface Command {
    String getName();
    void execute(String senderId, String[] args);
}