package com.example.bingo.dto;

import com.example.bingo.model.Winner;
import java.util.List;

public class ClaimResultResponse {
    private final boolean accepted;
    private final String message;
    private final List<Winner> winners;

    public ClaimResultResponse(boolean accepted, String message, List<Winner> winners) {
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
