package com.classic.preservitory.server.net;

import com.classic.preservitory.server.GameServer;

public class GameMessageHandler {

    private final GameServer server;

    public GameMessageHandler(GameServer server) {
        this.server = server;
    }

    public void handle(String id, String line) {
        if (line.isEmpty()) return;

        String[] parts = line.split(" ");

        if (parts.length == 4 && "UPDATE".equals(parts[0])) {
            try {
                server.updatePosition(id,
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3]));
            } catch (NumberFormatException ignored) {
                return;
            }

        } else if (parts.length >= 2 && "CHAT".equals(parts[0])) {
            String msg = line.length() > 5 ? line.substring(5) : "";
            server.broadcastChat(id, msg);

        } else if (parts.length == 2 && "CHOP".equals(parts[0])) {
            server.handleChop(id, parts[1]);

        } else if (parts.length == 2 && "MINE".equals(parts[0])) {
            server.handleMine(id, parts[1]);

        } else if (parts.length >= 2 && "ATTACK".equals(parts[0])) {
            server.handleAttack(id, parts[1]);

        } else if (parts.length == 2 && ("PICKUP".equals(parts[0]) || "PICKUP_ITEM".equals(parts[0]))) {
            server.handlePickup(id, parts[1]);

        } else if (parts.length == 3 && "LOGIN".equals(parts[0])) {
            server.handleLogin(id, parts[1], parts[2]);

        } else if (parts.length == 3 && "REGISTER".equals(parts[0])) {
            server.handleRegister(id, parts[1], parts[2]);

        } else if (parts.length == 2 && "TALK".equals(parts[0])) {
            server.handleTalk(id, parts[1]);

        } else if (parts.length >= 2 && "BUY".equals(parts[0])) {
            server.handleBuy(id, line.substring(4).trim());

        } else if (parts.length >= 2 && "SELL".equals(parts[0])) {
            server.handleSell(id, line.substring(5).trim());

        } else if ("SHOP_CLOSE".equals(parts[0])) {
            server.handleShopClose(id);

        } else if (parts.length == 2 && "COMBAT_STYLE".equals(parts[0])) {
            server.handleCombatStyle(id, parts[1]);

        } else if (parts.length == 2 && "EQUIP".equals(parts[0])) {
            try { server.handleEquip(id, Integer.parseInt(parts[1])); }
            catch (NumberFormatException ignored) {}

        } else if (parts.length == 2 && "UNEQUIP".equals(parts[0])) {
            server.handleUnequip(id, parts[1]);

        } else if ("DIALOGUE_NEXT".equals(parts[0])) {
            server.handleDialogueNext(id);

        } else if (line.startsWith("DIALOGUE_OPTION\t")) {
            String idx = line.substring(16);
            try {
                server.handleDialogueOption(id, Integer.parseInt(idx.trim()));
            } catch (NumberFormatException ignored) {}
        }
    }
}
