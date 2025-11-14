package com.example.bingo.service;

import com.example.bingo.dto.GameStateResponse;
import com.example.bingo.model.ClaimEvaluation;
import com.example.bingo.model.ClaimType;
import com.example.bingo.model.GameStatus;
import com.example.bingo.model.PlayerState;
import com.example.bingo.model.Scorecard;
import com.example.bingo.model.Winner;
import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GameService {

    private static final int SCORECARD_POOL_TARGET = 20;
    private static final int MAX_FULL_CARD_WINNERS = 3;

    private final SecureRandom random = new SecureRandom();
    private final KeywordRepository keywordRepository;
    private final Map<UUID, PlayerState> players = new ConcurrentHashMap<>();
    private final Map<String, Scorecard> cardPool = new LinkedHashMap<>();
    private final Set<String> assignedCardFingerprints = new LinkedHashSet<>();
    private final LinkedHashSet<String> calledPhrases = new LinkedHashSet<>();
    private final Deque<String> callQueue = new ArrayDeque<>();
    private final List<Winner> winners = new ArrayList<>();

    private GameStatus status = GameStatus.WAITING_FOR_HOST;
    private String currentCall;
    private Instant startedAt;

    public GameService(KeywordRepository keywordRepository) {
        this.keywordRepository = keywordRepository;
    }

    @PostConstruct
    public void boot() {
        resetGame(true);
    }

    public synchronized PlayerState registerPlayer(String requestedName) {
        String displayName = StringUtils.hasText(requestedName)
                ? requestedName.trim()
                : "Player-" + (players.size() + 1);
        PlayerState player = new PlayerState(UUID.randomUUID(), displayName, Instant.now());
        players.put(player.getId(), player);
        return player;
    }

    public synchronized PlayerState getPlayer(UUID playerId) {
        return Optional.ofNullable(players.get(playerId))
                .orElseThrow(() -> new IllegalArgumentException("Unknown player id"));
    }

    public synchronized List<Scorecard> previewScorecards(int count) {
        ensureCardPool(Math.max(count, SCORECARD_POOL_TARGET));
        List<Scorecard> cards = new ArrayList<>(cardPool.values());
        Collections.shuffle(cards, random);
        return cards.stream().limit(count).collect(Collectors.toList());
    }

    public synchronized PlayerState assignScorecard(UUID playerId, String scorecardId) {
        PlayerState player = getPlayer(playerId);
        Scorecard card = cardPool.remove(scorecardId);
        if (card == null) {
            throw new IllegalArgumentException("Scorecard already taken, please pick another");
        }
        if (player.getScorecard() != null) {
            assignedCardFingerprints.remove(player.getScorecard().fingerprint());
        }
        player.setScorecard(card);
        assignedCardFingerprints.add(card.fingerprint());
        return player;
    }

    public synchronized GameStateResponse startGame() {
        if (status == GameStatus.IN_PROGRESS) {
            return snapshot();
        }
        if (callQueue.isEmpty()) {
            refillCallQueue();
        }
        status = GameStatus.IN_PROGRESS;
        startedAt = Instant.now();
        currentCall = null;
        calledPhrases.clear();
        winners.clear();
        return snapshot();
    }

    public synchronized GameStateResponse resetGame(boolean dropPlayers) {
        status = GameStatus.WAITING_FOR_HOST;
        currentCall = null;
        calledPhrases.clear();
        winners.clear();
        startedAt = null;
        cardPool.clear();
        assignedCardFingerprints.clear();
        refillCallQueue();
        if (dropPlayers) {
            players.clear();
        } else {
            players.values().forEach(player -> player.setScorecard(null));
        }
        return snapshot();
    }

    public synchronized GameStateResponse drawNextNumber() {
        if (status == GameStatus.WAITING_FOR_HOST) {
            throw new IllegalStateException("Start the game before drawing numbers");
        }
        if (callQueue.isEmpty()) {
            status = GameStatus.COMPLETE;
            return snapshot();
        }
        currentCall = callQueue.removeFirst();
        calledPhrases.add(currentCall);
        if (callQueue.isEmpty()) {
            status = GameStatus.COMPLETE;
        }
        return snapshot();
    }

    public synchronized ClaimEvaluation claimWin(UUID playerId, ClaimType type) {
        PlayerState player = getPlayer(playerId);
        Scorecard card = player.getScorecard();
        if (card == null) {
            return new ClaimEvaluation(false, "Select a scorecard before claiming", List.copyOf(winners));
        }
        boolean matches = switch (type) {
            case ROW -> hasAnyRowComplete(card);
            case COLUMN -> hasAnyColumnComplete(card);
            case DIAGONAL -> hasDiagonalComplete(card);
            case FULL_CARD -> hasFullCard(card);
        };
        if (!matches) {
            return new ClaimEvaluation(false, "Squares not complete for this pattern", List.copyOf(winners));
        }
        boolean duplicateClaim = winners.stream()
                .anyMatch(existing -> existing.getPlayerId().equals(playerId) && existing.getClaimType() == type);
        if (duplicateClaim) {
            return new ClaimEvaluation(false, "Claim already recorded", List.copyOf(winners));
        }
        if (type == ClaimType.FULL_CARD) {
            long alreadyAwarded = winners.stream()
                    .filter(existing -> existing.getClaimType() == ClaimType.FULL_CARD)
                    .count();
            if (alreadyAwarded >= MAX_FULL_CARD_WINNERS) {
                return new ClaimEvaluation(false, "Three full-card winners already recorded", List.copyOf(winners));
            }
        }
        Winner winner = new Winner(player.getId(), player.getDisplayName(), type, Instant.now());
        winners.add(winner);
        if (type == ClaimType.FULL_CARD && winners.stream()
                .filter(existing -> existing.getClaimType() == ClaimType.FULL_CARD)
                .count() >= MAX_FULL_CARD_WINNERS) {
            status = GameStatus.COMPLETE;
        }
        return new ClaimEvaluation(true, "Claim accepted", List.copyOf(winners));
    }

    public synchronized GameStateResponse getCurrentState() {
        return snapshot();
    }

    private void ensureCardPool(int desiredSize) {
        List<String> keywords = keywordPool();
        while (cardPool.size() < desiredSize) {
            Scorecard candidate = ScorecardGenerator.create(random, keywords);
            if (assignedCardFingerprints.contains(candidate.fingerprint())) {
                continue;
            }
            cardPool.put(candidate.getId(), candidate);
        }
    }

    private void refillCallQueue() {
        callQueue.clear();
        List<String> keywords = new ArrayList<>(keywordPool());
        Collections.shuffle(keywords, random);
        callQueue.addAll(keywords);
    }

    private boolean hasAnyRowComplete(Scorecard card) {
        for (int row = 0; row < card.getSize(); row++) {
            boolean rowComplete = true;
            for (int col = 0; col < card.getSize(); col++) {
                if (!isMarked(card.getValue(row, col))) {
                    rowComplete = false;
                    break;
                }
            }
            if (rowComplete) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyColumnComplete(Scorecard card) {
        for (int col = 0; col < card.getSize(); col++) {
            boolean columnComplete = true;
            for (int row = 0; row < card.getSize(); row++) {
                if (!isMarked(card.getValue(row, col))) {
                    columnComplete = false;
                    break;
                }
            }
            if (columnComplete) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDiagonalComplete(Scorecard card) {
        boolean leftToRight = true;
        boolean rightToLeft = true;
        int size = card.getSize();
        for (int index = 0; index < size; index++) {
            if (!isMarked(card.getValue(index, index))) {
                leftToRight = false;
            }
            if (!isMarked(card.getValue(index, size - index - 1))) {
                rightToLeft = false;
            }
        }
        return leftToRight || rightToLeft;
    }

    private boolean hasFullCard(Scorecard card) {
        for (int row = 0; row < card.getSize(); row++) {
            for (int col = 0; col < card.getSize(); col++) {
                if (!isMarked(card.getValue(row, col))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isMarked(String phrase) {
        return ScorecardGenerator.FREE_SPACE.equals(phrase) || calledPhrases.contains(phrase);
    }

    private List<String> keywordPool() {
        List<String> keywords = keywordRepository.getKeywords();
        if (keywords.isEmpty()) {
            throw new IllegalStateException("Keyword list is empty");
        }
        return keywords;
    }

    private GameStateResponse snapshot() {
        List<String> called = List.copyOf(calledPhrases);
        return new GameStateResponse(
                status,
                currentCall,
                called,
                callQueue.size(),
                players.size(),
                List.copyOf(winners));
    }
}
