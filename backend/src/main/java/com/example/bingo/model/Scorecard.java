package com.example.bingo.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable representation of a Bingo scorecard.
 */
public final class Scorecard {

    private final String id;
    private final List<List<String>> rows;

    public Scorecard(String id, List<List<String>> rows) {
        this.id = Objects.requireNonNull(id, "id");
        List<List<String>> defensive = new ArrayList<>(rows.size());
        for (List<String> row : rows) {
            defensive.add(List.copyOf(row));
        }
        this.rows = Collections.unmodifiableList(defensive);
    }

    public String getId() {
        return id;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public int getSize() {
        return rows.size();
    }

    public String getValue(int row, int column) {
        return rows.get(row).get(column);
    }

    public String fingerprint() {
        return rows.stream()
                .flatMap(List::stream)
                .collect(Collectors.joining("-"));
    }

    @Override
    public String toString() {
        return "Scorecard{id='%s'}".formatted(id);
    }
}
