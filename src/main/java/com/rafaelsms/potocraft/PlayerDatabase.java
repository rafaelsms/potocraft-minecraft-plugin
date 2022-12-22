package com.rafaelsms.potocraft;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class PlayerDatabase {

    private static final String CREATE_ALLOWED_PLAYERS_TABLE = """
        create table if not exists allowedPlayers
        (
            playerId        varchar(36)                           not null,
            allowedPlayerId varchar(36)                           not null,
            allowedDate     timestamp default current_timestamp() not null on update current_timestamp(),
            primary key (playerId, allowedPlayerId)
        );
        """;
    private static final String CHECK_PLAYER_ALLOWED_BY_PLAYER = """
        SELECT EXISTS(
            SELECT playerId, allowedPlayerId
            FROM allowedPlayers
            WHERE playerId = ? AND allowedPlayerId = ?
        ) AS isPlayerAllowed;
        """;
    private static final String CHECK_PLAYERS_ALLOWED_BY_PLAYER = """
        SELECT allowedPlayerId
        FROM allowedPlayers
        WHERE playerId = ?;
        """;
    private static final String ADD_ALLOWED_PLAYER = """
        INSERT INTO allowedPlayers(playerId, allowedPlayerId)
        VALUES (?, ?);
        """;
    private static final String REMOVE_ALLOWED_PLAYER = """
        DELETE FROM allowedPlayers
        WHERE playerId = ? AND allowedPlayerId = ?;
        """;
    private static final String REMOVE_ALLOWED_PLAYERS = """
        DELETE FROM allowedPlayers
        WHERE playerId = ?;
        """;

    private final DatabasePool pool;

    public PlayerDatabase(DatabasePool pool) throws ExecutionException {
        this.pool = pool;
        createTable();
    }

    private void createTable() throws ExecutionException {
        pool.executeFuture(connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(CREATE_ALLOWED_PLAYERS_TABLE)) {
                preparedStatement.executeUpdate();
            }
            pool.getLogger().info("Created tables and indexes for allowedPlayers!");
        });
    }

    public List<UUID> getPlayersAllowed(UUID user) throws ExecutionException {
        return pool.executeFuture(connection -> {
            return getPlayersAllowed(connection, user);
        });
    }

    private List<UUID> getPlayersAllowed(Connection connection, UUID user) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(CHECK_PLAYERS_ALLOWED_BY_PLAYER)) {
            preparedStatement.setString(1, user.toString());

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                List<UUID> playerIds = new ArrayList<>();
                while (resultSet.next()) {
                    playerIds.add(UUID.fromString(resultSet.getString(1)));
                }
                return playerIds;
            }
        }
    }

    private boolean isPlayerAllowed(Connection connection, UUID user, UUID allowedPlayer) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(CHECK_PLAYER_ALLOWED_BY_PLAYER)) {
            preparedStatement.setString(1, user.toString());
            preparedStatement.setString(2, allowedPlayer.toString());

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    public boolean addAllowedPlayer(UUID user, UUID allowedPlayer) throws ExecutionException {
        return pool.executeFuture(connection -> {
            return setAllowedPlayer(connection, OperationType.ADD_PLAYER, user, allowedPlayer);
        });
    }

    public boolean removeAllowedPlayer(UUID user, UUID allowedPlayer) throws ExecutionException {
        return pool.executeFuture(connection -> {
            return setAllowedPlayer(connection, OperationType.REMOVE_PLAYER, user, allowedPlayer);
        });
    }

    public void removeAllowedPlayers(UUID user) throws ExecutionException {
        pool.executeFuture(connection -> {
            removeAllowedPlayers(connection, user);
        });
    }

    public void removeAllowedPlayers(Connection connection, UUID user) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(REMOVE_ALLOWED_PLAYERS)) {
            preparedStatement.setString(1, user.toString());
            preparedStatement.executeUpdate();
        }
    }

    private boolean setAllowedPlayer(Connection connection, OperationType operationType, UUID user, UUID allowedPlayer) throws SQLException {
        try {
            connection.setAutoCommit(false);

            boolean playerAllowed = isPlayerAllowed(connection, user, allowedPlayer);
            if (OperationType.ADD_PLAYER == operationType && playerAllowed) {
                return false;
            } else if (operationType == OperationType.REMOVE_PLAYER && !playerAllowed) {
                return false;
            }

            try (PreparedStatement preparedStatement = connection.prepareStatement(operationType.getQuery())) {
                preparedStatement.setString(1, user.toString());
                preparedStatement.setString(2, allowedPlayer.toString());
                return preparedStatement.executeUpdate() > 0;
            }
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private enum OperationType {

        ADD_PLAYER(ADD_ALLOWED_PLAYER),
        REMOVE_PLAYER(REMOVE_ALLOWED_PLAYER);

        private final String query;

        OperationType(String query) {
            this.query = query;
        }

        public String getQuery() {
            return query;
        }
    }
}
