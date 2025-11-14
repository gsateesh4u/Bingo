package com.example.bingo.model;

import java.util.List;

public class ClaimEvaluation {
    private final boolean accepted;
    private final String message;
    private final List<Winner> winners;

    public ClaimEvaluation(boolean accepted, String message, List<Winner> winners) {
        this.accepted = accepted;
        this.message = message;
        this.winners = winners;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getMessage() {
        return message;
    }

    public List<Winner> getWinners() {
        return winners;
    }
}
