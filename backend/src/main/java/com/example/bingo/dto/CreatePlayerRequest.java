package com.example.bingo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class CreatePlayerRequest {

    @NotNull(message = "Player ID is required")
    private UUID playerId;

    @Size(max = 40, message = "Display name must be 40 characters or less")
    private String displayName;

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
}
