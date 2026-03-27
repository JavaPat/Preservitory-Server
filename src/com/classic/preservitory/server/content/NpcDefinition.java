package com.classic.preservitory.server.content;

import java.util.LinkedHashMap;
import java.util.List;

public final class NpcDefinition {

    public final String id;
    public final String name;
    public final boolean shopkeeper;
    public final String questId;
    public final int questRewardCoins;
    public final List<String> dialogueStart;
    public final List<String> dialogueInProgress;
    public final List<String> dialogueReadyToComplete;
    public final List<String> dialogueComplete;
    public final LinkedHashMap<String, Integer> stockPrices;
    public final LinkedHashMap<String, Integer> sellPrices;

    public NpcDefinition(String id,
                         String name,
                         boolean shopkeeper,
                         String questId,
                         int questRewardCoins,
                         List<String> dialogueStart,
                         List<String> dialogueInProgress,
                         List<String> dialogueReadyToComplete,
                         List<String> dialogueComplete,
                         LinkedHashMap<String, Integer> stockPrices,
                         LinkedHashMap<String, Integer> sellPrices) {
        this.id = id;
        this.name = name;
        this.shopkeeper = shopkeeper;
        this.questId = questId;
        this.questRewardCoins = questRewardCoins;
        this.dialogueStart = dialogueStart;
        this.dialogueInProgress = dialogueInProgress;
        this.dialogueReadyToComplete = dialogueReadyToComplete;
        this.dialogueComplete = dialogueComplete;
        this.stockPrices = stockPrices;
        this.sellPrices = sellPrices;
    }
}
