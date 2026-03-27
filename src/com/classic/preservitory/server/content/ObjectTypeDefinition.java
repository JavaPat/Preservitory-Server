package com.classic.preservitory.server.content;

public final class ObjectTypeDefinition {

    public final String id;
    public final String category;
    public final long respawnMs;

    public ObjectTypeDefinition(String id, String category, long respawnMs) {
        this.id = id;
        this.category = category;
        this.respawnMs = respawnMs;
    }
}
