package com.example.bingo.dto;

import jakarta.validation.constraints.NotBlank;

public class SelectCardRequest {

    @NotBlank
    private String scorecardId;

    public String getScorecardId() {
        return scorecardId;
    }

    public void setScorecardId(String scorecardId) {
        this.scorecardId = scorecardId;
    }
}
