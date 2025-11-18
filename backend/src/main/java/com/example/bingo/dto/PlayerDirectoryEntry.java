package com.example.bingo.dto;

import java.util.UUID;

public class PlayerDirectoryEntry {

    private final UUID playerId;
    private final String displayName;
    private final boolean joined;
    private final boolean hasScorecard;

    public PlayerDirectoryEntry(UUID playerId, String displayName, boolean joined, boolean hasScorecard) {
        this.playerId = playerId;
        this.displayName = displayName;
        this.joined = joined;
        this.hasScorecard = hasScorecard;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isJoined() {
        return joined;
    }

    public boolean isHasScorecard() {
        return hasScorecard;
    }
}
