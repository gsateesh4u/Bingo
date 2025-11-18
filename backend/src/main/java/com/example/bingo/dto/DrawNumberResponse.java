package com.example.bingo.dto;

public class DrawNumberResponse extends GameStateResponse {
    public DrawNumberResponse(GameStateResponse delegate) {
        super(
                delegate.getStatus(),
                delegate.getCurrentCall(),
                delegate.getCurrentCallDetail(),
                delegate.getCalledPhrases(),
                delegate.getRemainingCalls(),
                delegate.getPlayerCount(),
                delegate.getWinners());
    }
}
