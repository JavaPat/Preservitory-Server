package com.classic.preservitory.server.net;

import com.classic.preservitory.server.player.PlayerSession;

import java.util.Map;

public class BroadcastService {

    private final Map<String, PlayerSession> sessions;

    public BroadcastService(Map<String, PlayerSession> sessions) {
        this.sessions = sessions;
    }

    public void broadcastAll(String message) {
        for (PlayerSession s : sessions.values()) {
            if (s.disconnected) continue;
            ClientHandler h = s.getHandler();
            if (h != null) {
                h.send(message);
            }
        }
    }

    public void broadcastPositions() {
        if (sessions.isEmpty()) return;

        StringBuilder sb = new StringBuilder("PLAYERS");

        for (Map.Entry<String, PlayerSession> entry : sessions.entrySet()) {
            PlayerSession s = entry.getValue();
            if (s.disconnected || !s.loggedIn) continue;
            sb.append(' ')
                    .append(entry.getKey())
                    .append(' ')
                    .append(s.x)
                    .append(' ')
                    .append(s.y)
                    .append(';');
        }

        broadcastAll(sb.toString());
    }

    public void sendToPlayer(String playerId, String message) {
        PlayerSession s = sessions.get(playerId);
        if (s != null) {
            ClientHandler h = s.getHandler();
            if (h != null) {
                h.send(message);
            }
        }
    }
}