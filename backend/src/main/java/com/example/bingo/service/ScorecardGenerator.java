package com.example.bingo.service;

import com.example.bingo.model.Scorecard;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Utility to generate 5x5 Bingo scorecards using provided keyword phrases.
 */
public final class ScorecardGenerator {

    public static final String FREE_SPACE = "FREE SPACE";

    private ScorecardGenerator() {
    }

    public static Scorecard create(SecureRandom random, List<String> keywords) {
        if (keywords.size() < 24) {
            throw new IllegalArgumentException("At least 24 keywords required to build a scorecard");
        }
        List<String> pool = new ArrayList<>(keywords);
        Collections.shuffle(pool, random);
        List<String> selection = new ArrayList<>(pool.subList(0, 25));

        List<List<String>> rows = new ArrayList<>();
        int index = 0;
        for (int row = 0; row < 5; row++) {
            List<String> rowValues = new ArrayList<>();
            for (int col = 0; col < 5; col++) {
                if (row == 2 && col == 2) {
                    rowValues.add(FREE_SPACE);
                } else {
                    rowValues.add(selection.get(index++));
                }
            }
            rows.add(rowValues);
        }
        return new Scorecard(UUID.randomUUID().toString(), rows);
    }
}
