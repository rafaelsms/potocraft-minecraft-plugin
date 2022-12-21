package com.rafaelsms.potocraft;

import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class BlockDatabase {

    private static final String CREATE_TABLE = """
        create table if not exists protectedBlocks
        (
            playerId       varchar(36)                            not null,
            worldId        varchar(36)                            not null,
            x              int                                    not null,
            y              int                                    not null,
            z              int                                    not null,
            dateModified   timestamp  default current_timestamp() not null on update current_timestamp(),
            temporaryBlock tinyint(1) default 1                   not null,
            primary key (worldId, x, y, z)
        );
        """;
    private static final String CREATE_DATE_MODIFIED_INDEX = """
        create index if not exists protectedBlocks__dateModified
            on protectedBlocks (dateModified);
        """;
    private static final String CREATE_PLAYER_ID_INDEX = """
        create index if not exists protectedBlocks__playerId
            on protectedBlocks (playerId);
        """;
    private static final String CREATE_TEMPORARY_BLOCK_INDEX = """
        create index if not exists protectedBlocks__temporaryBlock
            on protectedBlocks (temporaryBlock);
        """;

    private static final String IS_PROTECTED_BY_ANY = """
        SELECT playerId
        FROM protectedBlocks
        WHERE worldId = ?
            AND x BETWEEN ? AND ?
            AND y BETWEEN ? AND ?
            AND z BETWEEN ? AND ?
            AND dateModified >= current_timestamp() - INTERVAL ? DAY
            AND temporaryBlock = FALSE
        LIMIT 1;
        """;
    /**
     * Get protected blocks by players other than who we're checking for.
     * <p>
     * Block's owner should not be the player itself (UNION) or players that have allowed the player.
     */
    private static final String IS_PROTECTED_BY_OTHER_PLAYER = """
        SELECT playerId
        FROM protectedBlocks
        WHERE worldId = ?
            AND x BETWEEN ? AND ?
            AND y BETWEEN ? AND ?
            AND z BETWEEN ? AND ?
            AND dateModified >= current_timestamp() - INTERVAL ? DAY
            AND playerId NOT IN (
                SELECT allowedPlayers.playerId
                FROM allowedPlayers
                WHERE allowedPlayers.allowedPlayerId = ?
                UNION DISTINCT
                SELECT ? AS playerId
            )
            AND temporaryBlock = FALSE
        LIMIT 1;
        """;
    /**
     * Count protected blocks by players that are who we're searching for or players that the player is allowed for.
     * <p>
     * Block's owner should be the player itself (UNION) or players that have allowed the player.
     */
    private static final String COUNT_NEARBY_BLOCKS = """
        SELECT COUNT(*)
        FROM protectedBlocks
        WHERE worldId = ?
            AND x BETWEEN ? AND ?
            AND y BETWEEN ? AND ?
            AND z BETWEEN ? AND ?
            AND playerId IN (
                SELECT allowedPlayers.playerId
                FROM allowedPlayers
                WHERE allowedPlayers.allowedPlayerId = ?
                UNION DISTINCT
                SELECT ? AS playerId
            )
        """;
    private static final String ADD_BLOCK = """
        INSERT INTO protectedBlocks(worldId, x, y, z, temporaryBlock, playerId)
        VALUES (?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE temporaryBlock = ? AND playerId = ?;
        """;
    private static final String UPDATE_TEMPORARY_NEARBY_BLOCKS = """
        UPDATE protectedBlocks
        SET temporaryBlock = FALSE
        WHERE worldId = ?
            AND x BETWEEN ? AND ?
            AND y BETWEEN ? AND ?
            AND z BETWEEN ? AND ?
            AND playerId IN (
                SELECT allowedPlayers.playerId
                FROM allowedPlayers
                WHERE allowedPlayers.allowedPlayerId = ?
                UNION DISTINCT
                SELECT ? AS playerId
            )
            AND temporaryBlock = TRUE
        """;
    private static final String REMOVE_BLOCK = """
        DELETE IGNORE FROM protectedBlocks
        WHERE worldId = ?
            AND x = ?
            AND y = ?
            AND z = ?
        """;

    private final DatabasePool pool;

    public BlockDatabase(DatabasePool pool) throws ExecutionException {
        this.pool = pool;
        createTable();
    }

    private void createTable() throws ExecutionException {
        pool.executeFuture(connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(CREATE_TABLE)) {
                preparedStatement.executeUpdate();
            }
            try (PreparedStatement preparedStatement = connection.prepareStatement(CREATE_DATE_MODIFIED_INDEX)) {
                preparedStatement.executeUpdate();
            }
            try (PreparedStatement preparedStatement = connection.prepareStatement(CREATE_PLAYER_ID_INDEX)) {
                preparedStatement.executeUpdate();
            }
            try (PreparedStatement preparedStatement = connection.prepareStatement(CREATE_TEMPORARY_BLOCK_INDEX)) {
                preparedStatement.executeUpdate();
            }
            pool.getLogger().info("Created tables and indexes for protectedBlocks!");
        });
    }

    public Optional<UUID> getBlockOwnerToRead(Location location) throws ExecutionException {
        return pool.executeFuture(connection -> {
            return isBlockProtected(connection, location, Configuration.READ_DISTANCE_PROTECTION);
        });
    }

    public Optional<UUID> getBlockOwnerToNaturalAction(Location location) throws ExecutionException {
        return pool.executeFuture(connection -> {
            return isBlockProtected(connection, location, Configuration.NATURAL_DISTANCE_PROTECTION);
        });
    }

    public Optional<UUID> getBlockOwnerToWrite(Location location) throws ExecutionException {
        return pool.executeFuture(connection -> {
            return isBlockProtected(connection, location, Configuration.WRITE_DISTANCE_PROTECTION);
        });
    }

    public Optional<UUID> getBlockOwnerToRead(UUID playerId, Location location) throws ExecutionException {
        return pool.executeFuture(connection -> {
            if (playerId == null) {
                return isBlockProtected(connection, location, Configuration.READ_DISTANCE_PROTECTION);
            }
            return isBlockProtected(connection, playerId, location, Configuration.READ_DISTANCE_PROTECTION);
        });
    }

    public Optional<UUID> getBlockOwnerToWrite(UUID playerId, Location location) throws ExecutionException {
        return pool.executeFuture(connection -> {
            if (playerId == null) {
                return isBlockProtected(connection, location, Configuration.WRITE_DISTANCE_PROTECTION);
            }
            return isBlockProtected(connection, playerId, location, Configuration.WRITE_DISTANCE_PROTECTION);
        });
    }

    public void addProtectedBlock(UUID playerId, Location location) throws ExecutionException {
        pool.executeFuture(connection -> {
            addProtectedBlock(connection, playerId, location);
        });
    }

    public void removeBlock(Location location) throws ExecutionException {
        pool.executeFuture(connection -> {
            removeBlock(connection, location);
        });
    }

    public void removeBlocks(List<Location> locations) throws ExecutionException {
        pool.executeFuture(connection -> {
            removeBlocks(connection, locations);
        });
    }

    private void addProtectedBlock(Connection connection, UUID owner, Location location) throws SQLException {
        connection.setAutoCommit(false);

        boolean temporaryBlock;
        try (PreparedStatement preparedStatement = connection.prepareStatement(COUNT_NEARBY_BLOCKS)) {

            int i = setLocationStatement(location, Configuration.BLOCK_COUNT_SEARCH_RADIUS, preparedStatement, 0);
            preparedStatement.setString(++i, owner.toString());
            preparedStatement.setString(++i, owner.toString());

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    int blockCount = resultSet.getInt(1);
                    temporaryBlock = blockCount < Configuration.BLOCK_COUNT_TO_PROTECT;
                } else {
                    throw new SQLException("Query should have returned block count.");
                }
            }
        }

        try (PreparedStatement preparedStatement = connection.prepareStatement(ADD_BLOCK)) {
            int i = 0;

            preparedStatement.setString(++i, location.getWorld().getUID().toString());
            preparedStatement.setInt(++i, location.getBlockX());
            preparedStatement.setInt(++i, location.getBlockY());
            preparedStatement.setInt(++i, location.getBlockZ());
            preparedStatement.setBoolean(++i, temporaryBlock);
            preparedStatement.setString(++i, owner.toString());
            preparedStatement.setBoolean(++i, temporaryBlock);
            preparedStatement.setString(++i, owner.toString());

            preparedStatement.executeUpdate();
        }

        if (!temporaryBlock) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_TEMPORARY_NEARBY_BLOCKS)) {

                int i = setLocationStatement(location, Configuration.BLOCK_COUNT_SEARCH_RADIUS, preparedStatement, 0);
                preparedStatement.setString(++i, owner.toString());
                preparedStatement.setString(++i, owner.toString());

                preparedStatement.executeUpdate();
            }
        }

        connection.commit();
        connection.setAutoCommit(true);
    }

    private void removeBlock(Connection connection, Location location) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(REMOVE_BLOCK)) {
            int i = 0;

            preparedStatement.setString(++i, location.getWorld().getUID().toString());
            preparedStatement.setInt(++i, location.getBlockX());
            preparedStatement.setInt(++i, location.getBlockY());
            preparedStatement.setInt(++i, location.getBlockZ());

            preparedStatement.executeUpdate();
        }
    }

    private void removeBlocks(Connection connection, List<Location> locations) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(REMOVE_BLOCK)) {
            for (Location location : locations) {
                int i = 0;

                preparedStatement.setString(++i, location.getWorld().getUID().toString());
                preparedStatement.setInt(++i, location.getBlockX());
                preparedStatement.setInt(++i, location.getBlockY());
                preparedStatement.setInt(++i, location.getBlockZ());

                preparedStatement.addBatch();
            }

            preparedStatement.executeBatch();
        }
    }

    private Optional<UUID> isBlockProtected(Connection connection, Location location, int radius) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(IS_PROTECTED_BY_ANY)) {

            int i = setLocationStatement(location, radius, preparedStatement, 0);
            preparedStatement.setInt(++i, Configuration.DAYS_PROTECTED);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(UUID.fromString(resultSet.getString(1)));
                } else {
                    return Optional.empty();
                }
            }
        }
    }

    private Optional<UUID> isBlockProtected(Connection connection, UUID playerId, Location location, int radius)
        throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(IS_PROTECTED_BY_OTHER_PLAYER)) {

            int i = setLocationStatement(location, radius, preparedStatement, 0);
            preparedStatement.setInt(++i, Configuration.DAYS_PROTECTED);
            preparedStatement.setString(++i, playerId.toString());
            preparedStatement.setString(++i, playerId.toString());

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(UUID.fromString(resultSet.getString(1)));
                } else {
                    return Optional.empty();
                }
            }
        }
    }

    private static int setLocationStatement(Location location, int radius, PreparedStatement preparedStatement,
        int index) throws SQLException {
        int xMinus = location.getBlockX() - radius;
        int xPlus = location.getBlockX() + radius;
        int yMinus = location.getBlockY() - radius;
        int yPlus = location.getBlockY() + radius;
        int zMinus = location.getBlockZ() - radius;
        int zPlus = location.getBlockZ() + radius;


        preparedStatement.setString(++index, location.getWorld().getUID().toString());
        preparedStatement.setInt(++index, Math.min(xMinus, xPlus));
        preparedStatement.setInt(++index, Math.max(xMinus, xPlus));
        preparedStatement.setInt(++index, Math.min(yMinus, yPlus));
        preparedStatement.setInt(++index, Math.max(yMinus, yPlus));
        preparedStatement.setInt(++index, Math.min(zMinus, zPlus));
        preparedStatement.setInt(++index, Math.max(zMinus, zPlus));

        return index;
    }
}
