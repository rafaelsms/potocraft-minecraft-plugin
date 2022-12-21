package com.rafaelsms.potocraft;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class Configuration {

    public static final int NATURAL_DISTANCE_PROTECTION = 8;
    public static final int READ_DISTANCE_PROTECTION = 32;
    public static final int WRITE_DISTANCE_PROTECTION = READ_DISTANCE_PROTECTION * 2 + 1;
    public static final int DAYS_PROTECTED = 10;
    public static final int BLOCK_COUNT_SEARCH_RADIUS = 5;
    public static final int BLOCK_COUNT_TO_PROTECT = 26;

    private final FileConfiguration configuration;

    public Configuration(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        this.configuration = plugin.getConfig();
    }

    public String getSQLUser() {
        return (String) Objects.requireNonNull(configuration.get("sql.user"));
    }

    public String getSQLPassword() {
        return (String) Objects.requireNonNull(configuration.get("sql.password"));
    }

    public String getSQLJdbcUrl() {
        return (String) Objects.requireNonNull(configuration.get("sql.jdbcUrl"));
    }

    public String getSQLDriverClassName() {
        return (String) Objects.requireNonNull(configuration.get("sql.driverClassName"));
    }

    public int getSQLPoolSize() {
        return (int) Objects.requireNonNull(configuration.get("sql.poolSize"));
    }
}
