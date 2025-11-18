package com.example.bingo.controller;

import com.example.bingo.dto.ClaimRequest;
import com.example.bingo.dto.ClaimResultResponse;
import com.example.bingo.dto.CreatePlayerRequest;
import com.example.bingo.dto.DrawNumberResponse;
import com.example.bingo.dto.GameStateResponse;
import com.example.bingo.dto.PlayerResponse;
import com.example.bingo.dto.ScorecardListResponse;
import com.example.bingo.dto.SelectCardRequest;
import com.example.bingo.model.ClaimEvaluation;
import com.example.bingo.model.PlayerState;
import com.example.bingo.model.Scorecard;
import com.example.bingo.service.GameService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
@CrossOrigin(origins = "http://localhost:3000")
public class GameController {

    private final GameService gameService;
    private final String hostKey;

    public GameController(GameService gameService, @Value("${bingo.host-key}") String hostKey) {
        this.gameService = gameService;
        this.hostKey = hostKey;
    }

    @PostMapping(path = "/players", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PlayerResponse createPlayer(@Valid @RequestBody CreatePlayerRequest request) {
        PlayerState player = gameService.registerPlayer(request.getPlayerId(), request.getDisplayName());
        return toResponse(player);
    }

    @GetMapping("/players/{playerId}")
    public PlayerResponse getPlayer(@PathVariable UUID playerId) {
        return toResponse(gameService.getPlayer(playerId));
    }

    @GetMapping("/scorecards")
    public ScorecardListResponse getScorecards(@RequestParam(defaultValue = "6") int count) {
        List<Scorecard> cards = gameService.previewScorecards(Math.max(1, count));
        return new ScorecardListResponse(cards);
    }

    @PostMapping(path = "/players/{playerId}/scorecard", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PlayerResponse selectScorecard(
            @PathVariable UUID playerId,
            @Valid @RequestBody SelectCardRequest request) {
        PlayerState player = gameService.assignScorecard(playerId, request.getScorecardId());
        return toResponse(player);
    }

    @GetMapping("/game/state")
    public GameStateResponse getGameState() {
        return gameService.getCurrentState();
    }

    @PostMapping("/game/start")
    public GameStateResponse startGame(@RequestHeader("X-Host-Key") String providedHostKey) {
        assertHostAccess(providedHostKey);
        return gameService.startGame();
    }

    @PostMapping("/game/draw")
    public DrawNumberResponse drawNumber(@RequestHeader("X-Host-Key") String providedHostKey) {
        assertHostAccess(providedHostKey);
        return new DrawNumberResponse(gameService.drawNextNumber());
    }

    @PostMapping("/game/reset")
    public GameStateResponse resetGame(
            @RequestParam(defaultValue = "false") boolean dropPlayers,
            @RequestHeader("X-Host-Key") String providedHostKey) {
        assertHostAccess(providedHostKey);
        return gameService.resetGame(dropPlayers);
    }

    @PostMapping(path = "/game/claim", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ClaimResultResponse claim(
            @Valid @RequestBody ClaimRequest request,
            @RequestHeader("X-Host-Key") String providedHostKey) {
        assertHostAccess(providedHostKey);
        ClaimEvaluation evaluation = gameService.claimWin(request.getPlayerId(), request.getClaimType());
        return new ClaimResultResponse(evaluation.isAccepted(), evaluation.getMessage(), evaluation.getWinners());
    }

    private void assertHostAccess(String provided) {
        if (!Objects.equals(hostKey, provided)) {
            throw new IllegalArgumentException("Invalid host key");
        }
    }

    private PlayerResponse toResponse(PlayerState player) {
        return new PlayerResponse(player.getId(), player.getDisplayName(), player.getScorecard());
    }
}
