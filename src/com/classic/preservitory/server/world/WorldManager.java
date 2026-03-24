package com.classic.preservitory.server.world;

import com.classic.preservitory.server.objects.Tree;

import java.util.concurrent.ConcurrentHashMap;

public class WorldManager {

    private final ConcurrentHashMap<String, Tree> trees = new ConcurrentHashMap<>();

    public WorldManager() {
        initTrees();
    }

    private void initTrees() {
        int[][] positions = {
            {200, 200}, {300, 250}, {400, 300}, {150, 350}, {500, 200},
            {600, 400}, {250, 450}, {350, 150}, {450, 500}, {550, 300},
            {100, 200}, {700, 250}, {300, 550}, {650, 150}, {200, 600},
            {750, 350}, {400, 650}, {500, 100}, {100, 500}, {650, 600}
        };

        for (int i = 0; i < positions.length; i++) {
            String id = "W" + i;
            trees.put(id, new Tree(id, positions[i][0], positions[i][1]));
        }
    }

    public ConcurrentHashMap<String, Tree> getTrees() {
        return trees;
    }

    public String buildTreeState() {
        StringBuilder sb = new StringBuilder("TREES");
        for (Tree tree : trees.values()) {
            if (!tree.isAlive()) continue;
            sb.append(' ')
              .append(tree.getId())
              .append(' ')
              .append(tree.getX())
              .append(' ')
              .append(tree.getY())
              .append(';');
        }
        return sb.toString();
    }

    public void chopTree(String id) {
        Tree tree = trees.get(id);
        if (tree != null && tree.isAlive()) {
            tree.setAlive(false);
        }
    }
}