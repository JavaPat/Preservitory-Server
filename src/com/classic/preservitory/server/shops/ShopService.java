package com.classic.preservitory.server.shops;

import com.classic.preservitory.server.definitions.NpcDefinition;
import com.classic.preservitory.server.definitions.NpcDefinitionManager;
import com.classic.preservitory.server.net.BroadcastService;
import com.classic.preservitory.server.net.ClientHandler;
import com.classic.preservitory.server.npc.NPCData;
import com.classic.preservitory.server.player.PlayerSession;
import com.classic.preservitory.server.world.NPCManager;

import java.util.Map;

/**
 * Handles shop buy/sell transactions and the shop-close packet.
 *
 * Responsible for:
 *   - Validating that the player is logged in and has a shop open
 *   - Delegating item transactions to {@link Shop}
 *   - Sending the updated inventory snapshot after a successful transaction
 *   - Clearing shop state on close
 */
public class ShopService {

    private final Map<String, PlayerSession> sessions;
    private final ShopManager       shopManager;
    private final NPCManager        npcManager;
    private final BroadcastService  broadcastService;

    public ShopService(Map<String, PlayerSession> sessions,
                       ShopManager shopManager,
                       NPCManager npcManager,
                       BroadcastService broadcastService) {
        this.sessions         = sessions;
        this.shopManager      = shopManager;
        this.npcManager       = npcManager;
        this.broadcastService = broadcastService;
    }

    public void handleBuy(String playerId, String itemIdStr) {
        int itemId;
        try { itemId = Integer.parseInt(itemIdStr.trim()); } catch (NumberFormatException e) { return; }

        PlayerSession session = sessions.get(playerId);
        if (session == null) return;
        if (!session.loggedIn) {
            broadcastService.sendToPlayer(playerId, "SYSTEM Login first with /login <name> or /register <name>.");
            return;
        }
        if (!session.shopOpen) return;

        Shop shop = resolveShop(session);
        if (shop == null) { broadcastService.sendToPlayer(playerId, "SYSTEM Shop unavailable."); return; }

        String error = shop.buyItem(itemId, session.inventory);
        if (error != null) { broadcastService.sendToPlayer(playerId, "SYSTEM " + error); return; }

        ClientHandler h = session.getHandler();
        if (h != null) h.send(session.inventory.buildSnapshot());
        broadcastService.sendToPlayer(playerId, "SYSTEM Bought item " + itemId + ".");
    }

    public void handleSell(String playerId, String itemIdStr) {
        int itemId;
        try { itemId = Integer.parseInt(itemIdStr.trim()); } catch (NumberFormatException e) { return; }

        PlayerSession session = sessions.get(playerId);
        if (session == null) return;
        if (!session.loggedIn) {
            broadcastService.sendToPlayer(playerId, "SYSTEM Login first with /login <name> or /register <name>.");
            return;
        }
        if (!session.shopOpen) return;

        Shop shop = resolveShop(session);
        if (shop == null) { broadcastService.sendToPlayer(playerId, "SYSTEM Shop unavailable."); return; }

        String error = shop.sellItem(itemId, session.inventory);
        if (error != null) { broadcastService.sendToPlayer(playerId, "SYSTEM " + error); return; }

        ClientHandler h = session.getHandler();
        if (h != null) h.send(session.inventory.buildSnapshot());
        broadcastService.sendToPlayer(playerId, "SYSTEM Sold item " + itemId + ".");
    }

    public void handleShopClose(String playerId) {
        PlayerSession session = sessions.get(playerId);
        if (session == null) return;
        session.shopOpen    = false;
        session.activeNpcId = null;
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    /** Returns the Shop the player's active NPC sells, or null if not applicable. */
    private Shop resolveShop(PlayerSession session) {
        NPCData npc = session.activeNpcId != null ? npcManager.getNpc(session.activeNpcId) : null;
        if (npc == null) return null;
        NpcDefinition definition = NpcDefinitionManager.get(npc.definitionId);
        if (definition == null || definition.shopId == null) return null;
        return shopManager.getShop(definition.shopId);
    }
}
