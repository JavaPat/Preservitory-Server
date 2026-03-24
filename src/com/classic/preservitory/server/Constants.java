package com.classic.preservitory.server;

public class Constants {

    public static final String SERVER_NAME = "preservitory";

    /** The ip the client connects to. */
    public static final String LOCAL_HOST = "127.0.0.1";

    /** The port being used for the client to connect to. */
    public static final int PORT = 5555;

    /** How often the broadcast thread sends a PLAYERS snapshot (milliseconds). */
    public static final long BROADCAST_MS = 50;

}
