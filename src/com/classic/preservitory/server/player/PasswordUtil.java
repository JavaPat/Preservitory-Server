package com.classic.preservitory.server.player;

import org.mindrot.jbcrypt.BCrypt;

public final class PasswordUtil {

    private PasswordUtil() {}

    public static String hash(String password) {
        if (password == null || password.isBlank()) return null;
        return BCrypt.hashpw(password, BCrypt.gensalt(12));
    }

    public static boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }

        try {
            return BCrypt.checkpw(rawPassword, storedHash);
        } catch (Exception e) {
            return false;
        }
    }
}
