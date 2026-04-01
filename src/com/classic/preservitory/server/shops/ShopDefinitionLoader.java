package com.classic.preservitory.server.shops;

import com.classic.preservitory.server.definitions.ItemDefinitionManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads shop definitions from {@code cache/shops/*.json} and registers them in a
 * {@link ShopManager}.
 *
 * Example format:
 * <pre>
 * {
 *   "id":   "merchant_shop",
 *   "name": "Merchant's Shop",
 *   "stock": [
 *     { "itemId": 1001, "price": 40, "stock": -1 },
 *     { "itemId": 1002, "price": 40, "stock": -1 }
 *   ],
 *   "sellPrices": { "2": 3, "3": 6 }
 * }
 * </pre>
 */
public final class ShopDefinitionLoader {

    private static final Pattern ID_PATTERN   = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    private ShopDefinitionLoader() {}

    public static void loadAll(ShopManager manager) {
        for (Path dir : candidateDirs()) {
            if (!Files.isDirectory(dir)) continue;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path file : stream) {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    loadShop(json, manager);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load shop definitions from " + dir, e);
            }
            return;
        }
        System.out.println("[ShopDefinitionLoader] No shop definitions found.");
    }

    // -----------------------------------------------------------------------
    //  Per-file parsing
    // -----------------------------------------------------------------------

    private static void loadShop(String json, ShopManager manager) {
        String id   = match(json, ID_PATTERN);
        String name = match(json, NAME_PATTERN);
        if (id == null || name == null) {
            System.err.println("[ShopDefinitionLoader] Skipping malformed shop JSON.");
            return;
        }

        List<ShopItem>       stock      = parseStockArray(json);
        Map<Integer,Integer> sellPrices = parseSellPrices(json);

        // Validate all item IDs against the loaded definitions
        stock.removeIf(item -> {
            if (!ItemDefinitionManager.exists(item.itemId)) {
                System.err.println("[ShopDefinitionLoader] Unknown itemId " + item.itemId
                        + " in shop '" + id + "' — skipping.");
                return true;
            }
            return false;
        });
        sellPrices.keySet().removeIf(itemId -> {
            if (!ItemDefinitionManager.exists(itemId)) {
                System.err.println("[ShopDefinitionLoader] Unknown sell itemId " + itemId
                        + " in shop '" + id + "' — skipping.");
                return true;
            }
            return false;
        });

        manager.registerShop(id, name, stock, sellPrices);
        System.out.println("[ShopDefinitionLoader] Loaded shop: " + id
                + " (" + stock.size() + " items)");
    }

    private static List<ShopItem> parseStockArray(String json) {
        List<ShopItem> items = new ArrayList<>();
        String body = extractArrayBody(json, "stock");
        if (body == null || body.isBlank()) return items;

        Matcher obj = Pattern.compile("\\{([^}]+)\\}").matcher(body);
        while (obj.find()) {
            String block  = obj.group(1);
            String itemId = matchInline(block, "itemId");
            String price  = matchInline(block, "price");
            String stock  = matchInline(block, "stock");
            if (itemId == null || price == null) continue;
            try {
                items.add(new ShopItem(
                        Integer.parseInt(itemId.trim()),
                        Integer.parseInt(price.trim()),
                        stock != null ? Integer.parseInt(stock.trim()) : -1));
            } catch (NumberFormatException ignored) {}
        }
        return items;
    }

    private static Map<Integer, Integer> parseSellPrices(String json) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        String body = extractObjectBody(json, "sellPrices");
        if (body == null || body.isBlank()) return map;

        // Keys may be int or quoted-int: "2": 3  or  2: 3
        Matcher m = Pattern.compile("\"?(\\d+)\"?\\s*:\\s*(\\d+)").matcher(body);
        while (m.find()) {
            try {
                map.put(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
            } catch (NumberFormatException ignored) {}
        }
        return map;
    }

    // -----------------------------------------------------------------------
    //  JSON micro-parser helpers
    // -----------------------------------------------------------------------

    private static String match(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String matchInline(String block, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(?:\"([^\"]*)\"|(-?\\d+))");
        Matcher m = p.matcher(block);
        if (!m.find()) return null;
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    private static String extractArrayBody(String json, String fieldName) {
        int marker = json.indexOf("\"" + fieldName + "\"");
        if (marker == -1) return null;
        int start = json.indexOf('[', marker);
        if (start == -1) return null;
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            else if (c == ']' && --depth == 0) return json.substring(start + 1, i);
        }
        return null;
    }

    private static String extractObjectBody(String json, String fieldName) {
        int marker = json.indexOf("\"" + fieldName + "\"");
        if (marker == -1) return null;
        int start = json.indexOf('{', marker);
        if (start == -1) return null;
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return json.substring(start + 1, i);
        }
        return null;
    }

    private static Path[] candidateDirs() {
        return new Path[]{
            Paths.get("cache", "shops"),
            Paths.get("..", "Preservitory-Server", "cache", "shops"),
            Paths.get("..", "Preservitory", "cache", "shops")
        };
    }
}
