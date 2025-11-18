package com.example.bingo.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeywordInsight {

    private final String phrase;
    private final String title;
    private final String description;
    private final String sourceUrl;

    public KeywordInsight(String phrase, String title, String description, String sourceUrl) {
        this.phrase = phrase;
        this.title = title;
        this.description = description;
        this.sourceUrl = sourceUrl;
    }

    public String getPhrase() {
        return phrase;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }
}
