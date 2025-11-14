package com.example.bingo.dto;

import com.example.bingo.model.ClaimType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class ClaimRequest {

    @NotNull
    private UUID playerId;

    @NotNull
    private ClaimType claimType;

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    public ClaimType getClaimType() {
        return claimType;
    }

    public void setClaimType(ClaimType claimType) {
        this.claimType = claimType;
    }
}
