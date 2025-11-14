package com.example.bingo.dto;

import jakarta.validation.constraints.Size;

public class CreatePlayerRequest {

    @Size(max = 40, message = "Display name must be 40 characters or less")
    private String displayName;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
