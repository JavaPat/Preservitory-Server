package com.classic.preservitory.util;

import java.util.regex.Pattern;

/**
 * Stateless utility for validating and sanitising player chat messages.
 *
 * === What is allowed ===
 *   Letters     a-z  A-Z
 *   Digits      0-9
 *   Space
 *   Punctuation  .  ,  !  ?  '  -
 *
 * Everything else (control chars, emoji, HTML, protocol chars, etc.) is
 * stripped before the message travels over the network.
 *
 * === Pipeline (applied in order) ===
 *   1. Null / empty guard
 *   2. Strip forbidden characters (keep only the allowlist above)
 *   3. Collapse runs of whitespace to a single space + trim ends
 *   4. Truncate to MAX_LENGTH characters
 *   5. Replace blocked words with "***"
 *   6. Return null if nothing remains — callers must check for null
 *
 * === Usage ===
 *   String clean = ChatFilter.filter(raw);
 *   if (clean == null) { // show "Invalid message" feedback }
 *   else               { // send clean }
 *
 * The same class is used on both client and server so the logic is never
 * duplicated and the server can never be bypassed by a modified client.
 */
public final class ChatFilter {

    // -----------------------------------------------------------------------
    //  Constants
    // -----------------------------------------------------------------------

    /** Hard upper bound on message length (characters, after cleaning). */
    public static final int MAX_LENGTH = 80;

    /**
     * Regex that matches every character NOT in the allowlist.
     * Anything matched here will be removed.
     *
     *   [^a-zA-Z0-9 .,!?'\-]
     *
     * The hyphen must be last (or first) inside the character class to be
     * treated as a literal, not a range operator.
     */
    private static final Pattern FORBIDDEN = Pattern.compile("[^a-zA-Z0-9 .,!?'\\-]");

    /** Collapses any run of whitespace (spaces, tabs, etc.) to a single space. */
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    /**
     * Words that will be replaced with {@code "***"} in the cleaned output.
     * All comparisons are case-insensitive.
     *
     * This list is intentionally short — add entries as needed.
     * Each entry is compiled into a whole-word regex so "grass" does not
     * accidentally redact "badgrass".
     */
    private static final String[] BLOCKED_WORDS = {
            "idiot",
            "stupid",
            "dumb",
            "noob",
            "loser",
            "trash",
            "garbage",
            "moron",
            "retard",
            "kys",
            "kill yourself",
            "shut up",
            "hate",
            "toxic",
            "abuse",
            "nonsense",
            "useless",
            "pathetic",
            "annoying",
            "spam",
            "scam",
            "hack",
            "cheat",
            "exploit",
            "bot"
    };

    /** Pre-compiled patterns for each blocked word (whole-word, case-insensitive). */
    private static final Pattern[] BLOCKED_PATTERNS;

    static {
        BLOCKED_PATTERNS = new Pattern[BLOCKED_WORDS.length];
        for (int i = 0; i < BLOCKED_WORDS.length; i++) {
            // \\b = word boundary; Pattern.CASE_INSENSITIVE for easy matching
            String word = BLOCKED_WORDS[i].replace(" ", "\\s+");
            BLOCKED_PATTERNS[i] = Pattern.compile(
                    "(?i)\\b" + word + "\\b"
            );
        }
    }

    // -----------------------------------------------------------------------
    //  Private constructor — utility class, not instantiable
    // -----------------------------------------------------------------------

    private ChatFilter() {}

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    /**
     * Clean and validate a raw chat string.
     *
     * @param input  Raw text from the player (may be null)
     * @return       The cleaned message, or {@code null} if nothing usable remains
     */
    public static String filter(String input) {
        if (input == null) return null;

        // 1. Strip every character that isn't in the allowlist
        String clean = FORBIDDEN.matcher(input).replaceAll("");

        clean = clean.replaceAll("\\b([a-z])\\s+([a-z])\\s+([a-z])\\b", "$1$2$3");

        clean = clean.replaceAll("[.,!?'-]+", " ");

        clean = clean.replaceAll("(.)\\1{2,}", "$1$1");

        clean = clean.toLowerCase();

        // 2. Collapse whitespace runs, trim leading/trailing spaces
        clean = MULTI_SPACE.matcher(clean).replaceAll(" ").trim();

        // 3. Enforce length cap
        if (clean.length() > MAX_LENGTH) {
            clean = clean.substring(0, MAX_LENGTH).trim();
        }

        // 4. Nothing left after cleaning — treat as invalid
        if (clean.isEmpty()) return null;

        // 5. Replace blocked words
        for (Pattern p : BLOCKED_PATTERNS) {
            clean = p.matcher(clean).replaceAll("****");
        }

        return clean;
    }

    /**
     * Convenience check — true if {@code input} would produce a non-null result
     * from {@link #filter(String)}.  Useful for early guards without discarding
     * the cleaned value.
     */
    public static boolean isValid(String input) {
        return filter(input) != null;
    }
}
