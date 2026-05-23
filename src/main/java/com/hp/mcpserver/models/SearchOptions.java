package com.hp.mcpserver.models;

public record SearchOptions(
        boolean recursive,
        boolean searchContent,
        int maxDepth,
        int maxResults
) {
    public static SearchOptions defaults() {
        return new SearchOptions(true, true, 5, 50);
    }

    public static SearchOptions shallow() {
        return new SearchOptions(false, false, 1, 50);
    }
}
