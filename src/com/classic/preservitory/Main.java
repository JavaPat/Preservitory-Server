package com.classic.preservitory;

import com.classic.preservitory.server.Constants;
import com.classic.preservitory.server.GameServer;
import java.io.IOException;


public class Main {

    public static void main(String[] args) throws IOException {
        System.out.println("Starting " + Constants.SERVER_NAME + " server...");
        new GameServer().start();
    }
}
