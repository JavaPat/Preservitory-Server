package com.classic.preservitory.server.net;

import com.classic.preservitory.server.GameServer;
import com.classic.preservitory.server.player.PlayerSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final String id;
    private final GameServer server;
    private PrintWriter out;

    private final GameMessageHandler messageHandler;

    public ClientHandler(Socket socket, String id, GameServer server) {
        this.socket         = socket;
        this.id             = id;
        this.server         = server;
        this.messageHandler = new GameMessageHandler(server);
    }

    public String getId() {
        return id;
    }

    public void send(String message) {
        if (out != null) out.println(message);
    }

    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()))) {

            out = new PrintWriter(socket.getOutputStream(), true);
            out.println("WELCOME " + id);

            PlayerSession session = server.getSession(id);
            if (session != null) server.onConnect(session);

            String line;
            while ((line = in.readLine()) != null) {
                messageHandler.handle(id, line.trim());
            }

        } catch (IOException ignored) {
        } finally {
            server.removePlayer(id);
            if (!socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
