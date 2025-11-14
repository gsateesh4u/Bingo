package com.example.bingo.dto;

import com.example.bingo.model.Scorecard;

import java.util.UUID;

public class PlayerResponse {
    private UUID playerId;
    private String displayName;
    private Scorecard scorecard;

    public PlayerResponse(UUID playerId, String displayName, Scorecard scorecard) {
        this.playerId = playerId;
        this.displayName = displayName;
        this.scorecard = scorecard;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Scorecard getScorecard() {
        return scorecard;
    }
}
