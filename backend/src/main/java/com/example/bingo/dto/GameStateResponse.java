package com.example.bingo.dto;
import com.example.bingo.model.GameStatus;
import com.example.bingo.model.KeywordInsight;
import com.example.bingo.model.Winner;
import java.util.List;

public class GameStateResponse {
    private final GameStatus status;
    private final String currentCall;
    private final KeywordInsight currentCallDetail;
    private final List<String> calledPhrases;
    private final int remainingCalls;
    private final int playerCount;
    private final List<Winner> winners;

    public GameStateResponse(
            GameStatus status,
            String currentCall,
            KeywordInsight currentCallDetail,
            List<String> calledPhrases,
            int remainingCalls,
            int playerCount,
            List<Winner> winners) {
        this.status = status;
        this.currentCall = currentCall;
        this.currentCallDetail = currentCallDetail;
        this.calledPhrases = calledPhrases;
        this.remainingCalls = remainingCalls;
        this.playerCount = playerCount;
        this.winners = winners;
    }

    public GameStatus getStatus() {
        return status;
    }

    public String getCurrentCall() {
        return currentCall;
    }

    public KeywordInsight getCurrentCallDetail() {
        return currentCallDetail;
    }

    public List<String> getCalledPhrases() {
        return calledPhrases;
    }

    public int getRemainingCalls() {
        return remainingCalls;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public List<Winner> getWinners() {
        return winners;
    }
}
