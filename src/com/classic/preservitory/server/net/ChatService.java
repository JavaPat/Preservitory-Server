package com.classic.preservitory.server.net;

import com.classic.preservitory.server.commands.CommandManager;
import com.classic.preservitory.server.moderation.ModerationSystem;
import com.classic.preservitory.server.player.PlayerSession;
import com.classic.preservitory.util.ChatFilter;

import java.util.Map;

/**
 * Handles all inbound chat messages from players.
 *
 * Responsible for:
 *   - Detecting and dispatching :: / / commands to CommandManager
 *   - Running messages through ChatFilter and ModerationSystem
 *   - Broadcasting clean messages to all connected players
 */
public class ChatService {

    private final Map<String, PlayerSession> sessions;
    private final BroadcastService  broadcastService;
    private final CommandManager    commandManager;
    private final ModerationSystem  moderationSystem;

    public ChatService(Map<String, PlayerSession> sessions,
                       BroadcastService broadcastService,
                       CommandManager commandManager,
                       ModerationSystem moderationSystem) {
        this.sessions         = sessions;
        this.broadcastService = broadcastService;
        this.commandManager   = commandManager;
        this.moderationSystem = moderationSystem;
    }

    public void broadcastChat(String fromId, String message) {
        message = message.trim();

        if (message.startsWith("::") || message.startsWith("/")) {
            String command = message.replaceFirst("^[:/]+", "");
            if (!commandManager.handle(fromId, command)) {
                broadcastService.sendToPlayer(fromId, "SYSTEM Unknown command.");
            }
            return;
        }

        String clean = ChatFilter.filter(message);
        if (clean == null) {
            broadcastService.sendToPlayer(fromId, "SYSTEM Invalid message.");
            return;
        }

        ModerationSystem.Result result = moderationSystem.handleMessage(fromId, message, clean);
        if (!result.allowed) {
            if (result.message != null) broadcastService.sendToPlayer(fromId, "SYSTEM " + result.message);
            return;
        }
        if (result.message != null) broadcastService.sendToPlayer(fromId, "SYSTEM " + result.message);

        PlayerSession session = sessions.get(fromId);
        String role     = session.getRights().name();
        String username = session.username;

        System.out.println("[ChatService] CHAT [" + role + "] " + username + ": " + clean);
        broadcastService.broadcastAll("CHAT " + username + " " + role + " " + clean);
    }
}
