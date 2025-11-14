package com.example.bingo.dto;

import com.example.bingo.model.Scorecard;
import java.util.List;

public class ScorecardListResponse {
    private final List<Scorecard> scorecards;

    public ScorecardListResponse(List<Scorecard> scorecards) {
        this.scorecards = scorecards;
    }

    public List<Scorecard> getScorecards() {
        return scorecards;
    }
}
