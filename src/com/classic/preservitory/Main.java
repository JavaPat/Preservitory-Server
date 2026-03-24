package com.classic.preservitory;

import com.classic.preservitory.server.Constants;
import com.classic.preservitory.server.GameServer;
import java.io.IOException;

/**
 * Entry point for Preservitory.
 *
 * === Single-player (default) ===
 *   java com.classic.preservitory.Main
 *
 * === Multiplayer: start the server first ===
 *   java com.classic.preservitory.Main --server
 *
 * === Multiplayer: then launch one client per player ===
 *   java com.classic.preservitory.Main
 *   (Each client auto-connects to localhost:5555.)
 */
public class Main {

    public static void main(String[] args) throws IOException {
        // "--server" flag: run headless server, no game window
        if (args.length > 0 && "--server".equals(args[0])) {
            System.out.println("Starting " + Constants.SERVER_NAME + " server...");
            new GameServer().start();   // blocks forever
            return;
        }
    }
}
