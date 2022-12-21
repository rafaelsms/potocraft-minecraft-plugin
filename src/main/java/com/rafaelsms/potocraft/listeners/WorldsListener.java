package com.rafaelsms.potocraft.listeners;

import com.rafaelsms.potocraft.PotoCraftPlugin;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

public class WorldsListener implements Listener {

    private static final double BORDER_MAX_COORDINATE = 5_000.0;
    private static final int BORDER_WARNING_DISTANCE = 100;

    private final PotoCraftPlugin plugin;

    public WorldsListener(PotoCraftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    private void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        setWorldBorder(world);
        setWorldGameRules(world);
        setWorldDifficulty(world);
        plugin.logger().info("Enabled settings for {}", world.getName());
    }

    private static void setWorldBorder(World world) {
        WorldBorder worldBorder = world.getWorldBorder();
        if (world.getEnvironment() == World.Environment.NETHER) {
            worldBorder.setSize((2 * BORDER_MAX_COORDINATE) / world.getCoordinateScale());
        } else {
            worldBorder.setSize(2 * BORDER_MAX_COORDINATE);
        }
        worldBorder.setWarningDistance(BORDER_WARNING_DISTANCE);
        worldBorder.setCenter(0, 0);
    }

    private static void setWorldGameRules(World world) {
        world.setGameRule(GameRule.SPAWN_RADIUS, 100);
        world.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, 40);
        world.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false);
    }

    private static void setWorldDifficulty(World world) {
        world.setDifficulty(Difficulty.EASY);
    }
}
