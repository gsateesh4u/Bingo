package com.example.bingo.model;

import java.time.Instant;
import java.util.UUID;

public class Winner {
    private final UUID playerId;
    private final String displayName;
    private final ClaimType claimType;
    private final Instant timestamp;

    public Winner(UUID playerId, String displayName, ClaimType claimType, Instant timestamp) {
        this.playerId = playerId;
        this.displayName = displayName;
        this.claimType = claimType;
        this.timestamp = timestamp;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ClaimType getClaimType() {
        return claimType;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
