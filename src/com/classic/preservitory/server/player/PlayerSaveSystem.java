package com.classic.preservitory.server.player;

import java.io.*;

public class PlayerSaveSystem {

    private static final String SAVE_DIR = "players/";

    static {
        File dir = new File(SAVE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
            System.out.println("[SaveSystem] Created save directory.");
        }
    }

    // -----------------------------------------------------------------------
    //  Save
    // -----------------------------------------------------------------------

    public static void save(PlayerData data) {
        if (data == null || data.username == null) return;

        File file = new File(SAVE_DIR, data.username + ".dat");

        try (ObjectOutputStream out = new ObjectOutputStream(
                new FileOutputStream(file))) {

            out.writeObject(data);

        } catch (Exception e) {
            System.out.println("[SaveSystem] Failed to save player: " + data.username);
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------------------
    //  Load
    // -----------------------------------------------------------------------

    public static PlayerData load(String username) {
        if (username == null || username.isBlank()) return null;

        File file = new File(SAVE_DIR, username + ".dat");
        if (!file.exists()) return null;

        try (ObjectInputStream in = new ObjectInputStream(
                new FileInputStream(file))) {

            return (PlayerData) in.readObject();

        } catch (Exception e) {
            System.out.println("[SaveSystem] Failed to load player: " + username);
            return null; // ⚠️ prevents crash
        }
    }

    // -----------------------------------------------------------------------
    //  Exists
    // -----------------------------------------------------------------------

    public static boolean exists(String username) {
        if (username == null || username.isBlank()) return false;
        return new File(SAVE_DIR, username + ".dat").exists();
    }
}