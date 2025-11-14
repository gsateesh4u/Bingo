package com.example.bingo.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class PlayerState {

    private final UUID id;
    private final String displayName;
    private final Instant joinedAt;
    private Scorecard scorecard;

    public PlayerState(UUID id, String displayName, Instant joinedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = displayName;
        this.joinedAt = joinedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Scorecard getScorecard() {
        return scorecard;
    }

    public void setScorecard(Scorecard scorecard) {
        this.scorecard = scorecard;
    }
}
