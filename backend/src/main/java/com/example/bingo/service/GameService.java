package com.example.bingo.service;

import com.example.bingo.dto.GameStateResponse;
import com.example.bingo.model.ClaimEvaluation;
import com.example.bingo.model.ClaimType;
import com.example.bingo.model.GameStatus;
import com.example.bingo.model.PlayerState;
import com.example.bingo.model.Scorecard;
import com.example.bingo.model.Winner;
import com.example.bingo.repository.AccessPlayerRepository;
import com.example.bingo.repository.PlayerRecord;
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
    private final AccessPlayerRepository playerRepository;
    private final Map<UUID, PlayerState> players = new ConcurrentHashMap<>();
    private final Map<String, Scorecard> cardPool = new LinkedHashMap<>();
    private final Set<String> assignedCardFingerprints = new LinkedHashSet<>();
    private final LinkedHashSet<String> calledPhrases = new LinkedHashSet<>();
    private final Deque<String> callQueue = new ArrayDeque<>();
    private final List<Winner> winners = new ArrayList<>();

    private GameStatus status = GameStatus.WAITING_FOR_HOST;
    private String currentCall;
    private Instant startedAt;

    public GameService(KeywordRepository keywordRepository, AccessPlayerRepository playerRepository) {
        this.keywordRepository = keywordRepository;
        this.playerRepository = playerRepository;
    }

    @PostConstruct
    public void boot() {
        resetGame(true);
    }

    public synchronized PlayerState registerPlayer(UUID playerId, String requestedName) {
        PlayerState existing = players.get(playerId);
        if (existing != null) {
            return existing;
        }
        PlayerRecord record = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown player id"));
        boolean mutated = false;
        if (!StringUtils.hasText(record.getDisplayName())) {
            if (!StringUtils.hasText(requestedName)) {
                throw new IllegalArgumentException("Display name required the first time you join the game");
            }
            record.setDisplayName(requestedName.trim());
            mutated = true;
        }
        if (record.getJoinedAt() == null) {
            record.setJoinedAt(Instant.now());
            mutated = true;
        }
        if (mutated) {
            playerRepository.save(record);
        }
        PlayerState player = toPlayerState(record);
        players.put(player.getId(), player);
        if (player.getScorecard() != null) {
            assignedCardFingerprints.add(player.getScorecard().fingerprint());
        }
        return player;
    }

    public synchronized PlayerState getPlayer(UUID playerId) {
        PlayerState existing = players.get(playerId);
        if (existing != null) {
            return existing;
        }
        PlayerRecord record = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown player id"));
        if (!StringUtils.hasText(record.getDisplayName())) {
            throw new IllegalArgumentException("Player has not completed registration");
        }
        PlayerState player = toPlayerState(record);
        players.put(player.getId(), player);
        if (player.getScorecard() != null) {
            assignedCardFingerprints.add(player.getScorecard().fingerprint());
        }
        return player;
    }

    public synchronized List<Scorecard> previewScorecards(int count) {
        ensureCardPool(Math.max(count, SCORECARD_POOL_TARGET));
        List<Scorecard> cards = new ArrayList<>(cardPool.values());
        Collections.shuffle(cards, random);
        return cards.stream().limit(count).collect(Collectors.toList());
    }

    public synchronized PlayerState assignScorecard(UUID playerId, String scorecardId) {
        PlayerState player = getPlayer(playerId);
        if (status == GameStatus.IN_PROGRESS && player.getScorecard() != null) {
            throw new IllegalStateException("The round already started, scorecards are locked");
        }
        Scorecard card = cardPool.remove(scorecardId);
        if (card == null) {
            throw new IllegalArgumentException("Scorecard already taken, please pick another");
        }
        if (player.getScorecard() != null) {
            assignedCardFingerprints.remove(player.getScorecard().fingerprint());
        }
        player.setScorecard(card);
        assignedCardFingerprints.add(card.fingerprint());
        playerRepository.saveScorecard(player.getId(), card);
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
        playerRepository.clearAllScorecards();
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
            case COLUMN_1 -> hasColumnComplete(card, 0);
            case COLUMN_2 -> hasColumnComplete(card, 1);
            case COLUMN_3 -> hasColumnComplete(card, 2);
            case DIAGONAL -> hasDiagonalComplete(card);
            case FULL_CARD, FULL_CARD_FIRST, FULL_CARD_SECOND, FULL_CARD_THIRD -> hasFullCard(card);
        };
        if (!matches) {
            return new ClaimEvaluation(
                    false,
                    "Squares not complete for the %s pattern".formatted(describeClaim(type)),
                    List.copyOf(winners));
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
        } else if (isRankedFullCard(type)) {
            String violation = validateRankedFullCard(type);
            if (violation != null) {
                return new ClaimEvaluation(false, violation, List.copyOf(winners));
            }
        }
        Winner winner = new Winner(player.getId(), player.getDisplayName(), type, Instant.now());
        winners.add(winner);
        if (type == ClaimType.FULL_CARD && winners.stream()
                .filter(existing -> existing.getClaimType() == ClaimType.FULL_CARD)
                .count() >= MAX_FULL_CARD_WINNERS) {
            status = GameStatus.COMPLETE;
        }
        if (type == ClaimType.FULL_CARD_THIRD) {
            status = GameStatus.COMPLETE;
        }
        return new ClaimEvaluation(true, "Claim accepted", List.copyOf(winners));
    }

    public synchronized GameStateResponse getCurrentState() {
        return snapshot();
    }

    private PlayerState toPlayerState(PlayerRecord record) {
        if (!StringUtils.hasText(record.getDisplayName())) {
            throw new IllegalStateException("Player record is missing a display name");
        }
        Instant joinedAt = record.getJoinedAt() == null ? Instant.now() : record.getJoinedAt();
        PlayerState player = new PlayerState(record.getPlayerId(), record.getDisplayName(), joinedAt);
        player.setScorecard(record.getScorecard());
        return player;
    }

    private String describeClaim(ClaimType type) {
        return switch (type) {
            case ROW -> "row";
            case COLUMN -> "column";
            case COLUMN_1 -> "first column";
            case COLUMN_2 -> "second column";
            case COLUMN_3 -> "third column";
            case DIAGONAL -> "diagonal";
            case FULL_CARD -> "full card";
            case FULL_CARD_FIRST -> "full card (first winner)";
            case FULL_CARD_SECOND -> "full card (second winner)";
            case FULL_CARD_THIRD -> "full card (third winner)";
        };
    }

    private boolean isRankedFullCard(ClaimType type) {
        return type == ClaimType.FULL_CARD_FIRST
                || type == ClaimType.FULL_CARD_SECOND
                || type == ClaimType.FULL_CARD_THIRD;
    }

    private String validateRankedFullCard(ClaimType type) {
        boolean firstRecorded = hasWinner(ClaimType.FULL_CARD_FIRST);
        boolean secondRecorded = hasWinner(ClaimType.FULL_CARD_SECOND);
        boolean thirdRecorded = hasWinner(ClaimType.FULL_CARD_THIRD);
        return switch (type) {
            case FULL_CARD_FIRST -> firstRecorded ? "First full-card winner already recorded" : null;
            case FULL_CARD_SECOND -> {
                if (!firstRecorded) {
                    yield "Record the first full-card winner before the second";
                }
                yield secondRecorded ? "Second full-card winner already recorded" : null;
            }
            case FULL_CARD_THIRD -> {
                if (!firstRecorded || !secondRecorded) {
                    yield "Record the first and second full-card winners before the third";
                }
                yield thirdRecorded ? "Third full-card winner already recorded" : null;
            }
            default -> null;
        };
    }

    private boolean hasWinner(ClaimType type) {
        return winners.stream().anyMatch(existing -> existing.getClaimType() == type);
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

    private boolean hasColumnComplete(Scorecard card, int columnIndex) {
        if (columnIndex < 0 || columnIndex >= card.getSize()) {
            return false;
        }
        for (int row = 0; row < card.getSize(); row++) {
            if (!isMarked(card.getValue(row, columnIndex))) {
                return false;
            }
        }
        return true;
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
