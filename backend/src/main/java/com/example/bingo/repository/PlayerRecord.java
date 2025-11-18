package com.example.bingo.repository;

import com.example.bingo.model.Scorecard;
import java.time.Instant;
import java.util.UUID;

/**
 * Simple DTO that mirrors the player row persisted in the Access database.
 */
public class PlayerRecord {

    private UUID playerId;
    private String displayName;
    private Instant joinedAt;
    private Scorecard scorecard;

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    public Scorecard getScorecard() {
        return scorecard;
    }

    public void setScorecard(Scorecard scorecard) {
        this.scorecard = scorecard;
    }
}
