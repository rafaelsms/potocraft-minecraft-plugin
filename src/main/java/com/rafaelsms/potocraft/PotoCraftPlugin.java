package com.rafaelsms.potocraft;

import com.rafaelsms.potocraft.commands.AllowCommand;
import com.rafaelsms.potocraft.commands.AllowListCommand;
import com.rafaelsms.potocraft.commands.DisallowCommand;
import com.rafaelsms.potocraft.databases.BlockDatabase;
import com.rafaelsms.potocraft.databases.DatabasePool;
import com.rafaelsms.potocraft.databases.PlayerDatabase;
import com.rafaelsms.potocraft.listeners.BlocksListener;
import com.rafaelsms.potocraft.listeners.CombatListener;
import com.rafaelsms.potocraft.listeners.WorldsListener;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class PotoCraftPlugin extends JavaPlugin {

    private Configuration configuration;
    private DatabasePool databasePool;

    private PlayerDatabase playerDatabase;
    private BlockDatabase blockDatabase;

    private Messages messages;

    @Override
    public void onEnable() {
        Permission.registerPermissions(this);

        this.configuration = new Configuration(this);
        this.databasePool = new DatabasePool(configuration, getSLF4JLogger());

        try {
            this.playerDatabase = new PlayerDatabase(databasePool);
            this.blockDatabase = new BlockDatabase(databasePool);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to initialize database: ", e);
        }

        this.messages = new Messages(this);

        registerEvent(new WorldsListener(this));
        registerEvent(new BlocksListener(this));
        registerEvent(new CombatListener(this));

        registerCommand("allow", new AllowCommand(this));
        registerCommand("allowlist", new AllowListCommand(this));
        registerCommand("disallow", new DisallowCommand(this));

        getLogger().info("Enabled PotoCraft Plugin.");
    }

    @Override
    public void onDisable() {
        // Stop listeners from this plugin
        HandlerList.unregisterAll(this);

        try {
            this.databasePool.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        getLogger().info("Disabled PotoCraft Plugin.");
    }

    public Logger logger() {
        return getSLF4JLogger();
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public PlayerDatabase getPlayerDatabase() {
        return playerDatabase;
    }

    public BlockDatabase getBlockDatabase() {
        return blockDatabase;
    }

    public Messages getMessages() {
        return messages;
    }

    private void registerEvent(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    private void registerCommand(String command, CommandExecutor commandExecutor) {
        PluginCommand pluginCommand = getServer().getPluginCommand(command);
        if (pluginCommand == null) {
            throw new IllegalStateException("Attempted to register unknown command: %s".formatted(command));
        }
        pluginCommand.setExecutor(commandExecutor);
    }

    public OfflinePlayer searchOfflinePlayer(String playerName) {
        OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(playerName);
        if (offlinePlayer.hasPlayedBefore()) {
            return offlinePlayer;
        }

        return null;
    }
}
