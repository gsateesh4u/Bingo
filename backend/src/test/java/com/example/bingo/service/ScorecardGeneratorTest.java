package com.example.bingo.service;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.bingo.model.Scorecard;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ScorecardGeneratorTest {

    @Test
    void generatesKeywordCard() {
        List<String> keywords = IntStream.range(0, 40)
                .mapToObj(i -> "Keyword " + i)
                .toList();
        Scorecard card = ScorecardGenerator.create(new SecureRandom(), keywords);
        assertThat(card.getRows()).hasSize(5);
        card.getRows().forEach(row -> assertThat(row).hasSize(5));
        assertThat(card.getValue(2, 2)).isEqualTo(ScorecardGenerator.FREE_SPACE);
        // Each entry (except free space) must come from keywords
        card.getRows().forEach(row -> row.forEach(entry -> {
            if (!ScorecardGenerator.FREE_SPACE.equals(entry)) {
                assertThat(keywords).contains(entry);
            }
        }));
    }
}
