package com.example.bingo.repository;

import com.example.bingo.model.Scorecard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthmarketscience.jackcess.ColumnBuilder;
import com.healthmarketscience.jackcess.Cursor;
import com.healthmarketscience.jackcess.CursorBuilder;
import com.healthmarketscience.jackcess.DataType;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Row;
import com.healthmarketscience.jackcess.Table;
import com.healthmarketscience.jackcess.TableBuilder;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

/**
 * Repository that keeps the canonical list of players inside a Microsoft Access database file.
 * The file is created on demand and bootstrapped with optional seed data when it is empty.
 */
@Repository
public class AccessPlayerRepository {

    private static final String TABLE_NAME = "players";
    private static final TypeReference<List<List<String>>> ROWS_TYPE =
            new TypeReference<List<List<String>>>() {};

    private final Path databasePath;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String seedLocation;

    public AccessPlayerRepository(
            @Value("${bingo.access-db.path}") String databasePath,
            @Value("${bingo.access-db.seed:}") String seedLocation,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader) {
        this.databasePath = Path.of(databasePath).toAbsolutePath();
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.seedLocation = seedLocation;
    }

    @PostConstruct
    public void initialize() {
        try {
            createDatabaseIfMissing();
            ensureTable();
            seedIfEmpty();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to initialize Access player repository", ex);
        }
    }

    public synchronized Optional<PlayerRecord> findById(UUID playerId) {
        try (Database database = openDatabase()) {
            Table table = database.getTable(TABLE_NAME);
            if (table == null) {
                return Optional.empty();
            }
            for (Row row : table) {
                if (playerId.toString().equals(row.get("player_id"))) {
                    return Optional.of(mapRow(row));
                }
            }
            return Optional.empty();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read from Access database", ex);
        }
    }

    public synchronized void save(PlayerRecord record) {
        try (Database database = openDatabase()) {
            Table table = database.getTable(TABLE_NAME);
            if (table == null) {
                throw new IllegalStateException("Players table missing in Access database");
            }
            Map<String, Object> rowData = toRowMap(record);
            Cursor cursor = CursorBuilder.createCursor(table);
            boolean updated = false;
            while (cursor.moveToNextRow()) {
                Row current = cursor.getCurrentRow();
                if (record.getPlayerId().toString().equals(current.get("player_id"))) {
                    cursor.updateCurrentRowFromMap(rowData);
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                table.addRowFromMap(rowData);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist player to Access database", ex);
        }
    }

    public synchronized void saveScorecard(UUID playerId, Scorecard scorecard) {
        PlayerRecord record = findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown player id"));
        record.setScorecard(scorecard);
        save(record);
    }

    public synchronized void clearAllScorecards() {
        try (Database database = openDatabase()) {
            Table table = database.getTable(TABLE_NAME);
            if (table == null) {
                return;
            }
            Cursor cursor = CursorBuilder.createCursor(table);
            while (cursor.moveToNextRow()) {
                Map<String, Object> row = new HashMap<>(cursor.getCurrentRow());
                row.put("scorecard_id", null);
                row.put("scorecard_payload", null);
                cursor.updateCurrentRowFromMap(row);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to clear scorecards in Access database", ex);
        }
    }

    private void createDatabaseIfMissing() throws IOException {
        if (Files.exists(databasePath)) {
            return;
        }
        Path parent = databasePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        DatabaseBuilder.create(Database.FileFormat.V2010, databasePath.toFile()).close();
    }

    private void ensureTable() throws IOException {
        try (Database database = openDatabase()) {
            if (database.getTable(TABLE_NAME) != null) {
                return;
            }
            new TableBuilder(TABLE_NAME)
                    .addColumn(new ColumnBuilder("player_id", DataType.MEMO))
                    .addColumn(new ColumnBuilder("display_name", DataType.TEXT).setLength(255))
                    .addColumn(new ColumnBuilder("joined_at", DataType.SHORT_DATE_TIME))
                    .addColumn(new ColumnBuilder("scorecard_id", DataType.TEXT).setLength(64))
                    .addColumn(new ColumnBuilder("scorecard_payload", DataType.MEMO))
                    .toTable(database);
        }
    }

    private void seedIfEmpty() throws IOException {
        if (!StringUtils.hasText(seedLocation)) {
            return;
        }
        Resource resource = resourceLoader.getResource(seedLocation);
        if (!resource.exists()) {
            return;
        }
        try (Database database = openDatabase()) {
            Table table = database.getTable(TABLE_NAME);
            if (table == null || table.getRowCount() > 0) {
                return;
            }
            List<PlayerSeed> seeds = loadSeeds(resource);
            Instant now = Instant.now();
            for (PlayerSeed seed : seeds) {
                PlayerRecord record = new PlayerRecord();
                record.setPlayerId(seed.playerId());
                record.setDisplayName(seed.displayName());
                record.setJoinedAt(now);
                table.addRowFromMap(toRowMap(record));
            }
        }
    }

    private List<PlayerSeed> loadSeeds(Resource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            byte[] bytes = StreamUtils.copyToByteArray(inputStream);
            return objectMapper.readValue(bytes, new TypeReference<List<PlayerSeed>>() {});
        }
    }

    private PlayerRecord mapRow(Row row) {
        PlayerRecord record = new PlayerRecord();
        record.setPlayerId(UUID.fromString((String) row.get("player_id")));
        record.setDisplayName((String) row.get("display_name"));
        Date joined = (Date) row.get("joined_at");
        if (joined != null) {
            record.setJoinedAt(joined.toInstant());
        }
        String payload = (String) row.get("scorecard_payload");
        if (StringUtils.hasText(payload)) {
            record.setScorecard(readScorecard(payload));
        }
        return record;
    }

    private Map<String, Object> toRowMap(PlayerRecord record) {
        Map<String, Object> row = new HashMap<>();
        row.put("player_id", record.getPlayerId().toString());
        row.put("display_name", record.getDisplayName());
        Instant joinedAt = record.getJoinedAt();
        row.put("joined_at", joinedAt == null ? null : Date.from(joinedAt));
        Scorecard card = record.getScorecard();
        row.put("scorecard_id", card == null ? null : card.getId());
        row.put("scorecard_payload", card == null ? null : writeScorecard(card));
        return row;
    }

    private Database openDatabase() throws IOException {
        return DatabaseBuilder.open(databasePath.toFile());
    }

    private String writeScorecard(Scorecard card) {
        try {
            ScorecardPayload payload = new ScorecardPayload(card.getId(), card.getRows());
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize scorecard for Access storage", ex);
        }
    }

    private Scorecard readScorecard(String payload) {
        try {
            ScorecardPayload scorecardPayload = objectMapper.readValue(payload, ScorecardPayload.class);
            List<List<String>> rows = objectMapper.convertValue(scorecardPayload.rows(), ROWS_TYPE);
            return new Scorecard(scorecardPayload.id(), rows);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to deserialize scorecard payload", ex);
        }
    }

    private record ScorecardPayload(String id, List<List<String>> rows) {
    }

    private record PlayerSeed(UUID playerId, String displayName) {
    }
}
