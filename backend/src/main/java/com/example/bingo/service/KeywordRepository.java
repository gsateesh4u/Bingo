package com.example.bingo.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class KeywordRepository {

    private final List<String> keywords;

    public KeywordRepository(@Value("classpath:keywords.txt") Resource keywordsResource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(keywordsResource.getInputStream(), StandardCharsets.UTF_8))) {
            this.keywords = reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toUnmodifiableList());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load keywords", e);
        }
    }

    public List<String> getKeywords() {
        return keywords;
    }
}
