package com.example.bingo.dto;

import java.util.List;

public class PlayerDirectoryResponse {

    private final List<PlayerDirectoryEntry> players;

    public PlayerDirectoryResponse(List<PlayerDirectoryEntry> players) {
        this.players = players;
    }

    public List<PlayerDirectoryEntry> getPlayers() {
        return players;
    }
}
