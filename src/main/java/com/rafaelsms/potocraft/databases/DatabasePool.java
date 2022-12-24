package com.rafaelsms.potocraft.databases;

import com.rafaelsms.potocraft.Configuration;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class DatabasePool implements Closeable {

    private final HikariDataSource dataSource;
    private final ExecutorService executor;
    private final Logger logger;

    private static int thread = 0;
    private static final ThreadFactory factory =
        r -> new Thread(r, "PotoCraft HikariCP Worker thread %d".formatted(thread++));

    public DatabasePool(Configuration configuration, Logger logger) {
        this.logger = logger;

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(configuration.getSQLDriverClassName());
        hikariConfig.setJdbcUrl(configuration.getSQLJdbcUrl());
        hikariConfig.setUsername(configuration.getSQLUser());
        hikariConfig.setPassword(configuration.getSQLPassword());
        hikariConfig.setMaximumPoolSize(configuration.getSQLPoolSize());

        this.dataSource = new HikariDataSource(hikariConfig);
        this.executor = Executors.newFixedThreadPool(hikariConfig.getMaximumPoolSize(), factory);
    }

    @Override
    public void close() throws IOException {
        try {
            getLogger().info("Shutting down executor pool...");
            this.executor.shutdown();
            if (!this.executor.awaitTermination(1, TimeUnit.MINUTES)) {
                getLogger().info("Timeout on executor shutdown elapsed... Ignoring executing tasks and shutting down anyway.");
                this.executor.shutdownNow();
            }
            getLogger().info("Shutting down database pool...");
            this.dataSource.close();
            getLogger().info("Database pool shutdown.");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T executeFuture(DatabaseCallable<T> callable) throws ExecutionException {
        return handleFuture(execute(callable));
    }

    public void executeFuture(DatabaseRunnable runnable) throws ExecutionException {
        handleFuture(execute(runnable));
    }

    public Future<Void> execute(DatabaseRunnable runnable) {
        return execute(connection -> {
            runnable.run(connection);
            return null;
        });
    }

    public <T> Future<T> execute(DatabaseCallable<T> callable) {
        return executor.submit(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return callable.call(connection);
            } catch (SQLException exception) {
                logger.warn("Failed to execute query: ", exception);
                throw exception;
            }
        });
    }

    public <T> T handleFuture(Future<T> future) throws ExecutionException {
        try {
            return future.get();
        } catch (InterruptedException | CancellationException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new ExecutionException(e);
        }
    }

    public Logger getLogger() {
        return logger;
    }

    public interface DatabaseRunnable {
        void run(Connection connection) throws Exception;
    }

    public interface DatabaseCallable<T> {
        T call(Connection connection) throws Exception;
    }
}
