package com.rafaelsms.potocraft.listeners;

import com.rafaelsms.potocraft.PotoCraftPlugin;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class CombatListener implements Listener {

    private final static int TICKS_PER_SECOND = 20;

    private final static Map<EntityDamageEvent.DamageCause, CombatType> CAUSE_COMBAT_TYPE_MAP;

    static {
        Map<EntityDamageEvent.DamageCause, CombatType> causeCombatMap = new HashMap<>();

        // By default, every damage source will be self-inflicted
        for (EntityDamageEvent.DamageCause cause : EntityDamageEvent.DamageCause.values()) {
            causeCombatMap.put(cause, CombatType.SELF_INFLICTED);
        }

        // Will always inflict PVE combat
        causeCombatMap.put(EntityDamageEvent.DamageCause.ENTITY_ATTACK, CombatType.PLAYER_VERSUS_ENTITY);
        causeCombatMap.put(EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK, CombatType.PLAYER_VERSUS_ENTITY);
        causeCombatMap.put(EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, CombatType.PLAYER_VERSUS_ENTITY);
        causeCombatMap.put(EntityDamageEvent.DamageCause.POISON, CombatType.PLAYER_VERSUS_ENTITY);
        causeCombatMap.put(EntityDamageEvent.DamageCause.DRAGON_BREATH, CombatType.PLAYER_VERSUS_ENTITY);
        causeCombatMap.put(EntityDamageEvent.DamageCause.WITHER, CombatType.PLAYER_VERSUS_ENTITY);
        causeCombatMap.put(EntityDamageEvent.DamageCause.MAGIC, CombatType.PLAYER_VERSUS_ENTITY);
        causeCombatMap.put(EntityDamageEvent.DamageCause.PROJECTILE, CombatType.PLAYER_VERSUS_ENTITY);
        causeCombatMap.put(EntityDamageEvent.DamageCause.SONIC_BOOM, CombatType.PLAYER_VERSUS_ENTITY);

        // This will not show boss bar at all (won't block commands)
        causeCombatMap.remove(EntityDamageEvent.DamageCause.CUSTOM);
        causeCombatMap.remove(EntityDamageEvent.DamageCause.FALL);
        causeCombatMap.remove(EntityDamageEvent.DamageCause.DRYOUT);
        causeCombatMap.remove(EntityDamageEvent.DamageCause.SUICIDE);
        causeCombatMap.remove(EntityDamageEvent.DamageCause.FLY_INTO_WALL);

        CAUSE_COMBAT_TYPE_MAP = Collections.unmodifiableMap(causeCombatMap);
    }

    private final Map<UUID, CombatEntry> combatEntries = new HashMap<>();
    private final Set<String> blockedCommands;
    private final PotoCraftPlugin plugin;

    public CombatListener(PotoCraftPlugin plugin) {
        this.plugin = plugin;

        Set<String> blockedCommandSet = new HashSet<>();
        for (String command : plugin.getConfiguration().getBlockedCommands()) {
            blockedCommandSet.add(command.toLowerCase());
        }
        this.blockedCommands = Collections.unmodifiableSet(blockedCommandSet);

        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickCombatEntries, 1, 1);
    }

    private void tickCombatEntries() {
        Iterator<Map.Entry<UUID, CombatEntry>> iterator = combatEntries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, CombatEntry> entry = iterator.next();

            // Tick and remove if it has finished
            entry.getValue().tick();
            if (entry.getValue().hasFinished()) {
                iterator.remove();
            }
        }
    }

    @EventHandler
    private void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        // Do not remove from the map because we need it on death event below
        CombatEntry combatEntry = this.combatEntries.get(event.getPlayer().getUniqueId());

        // Ignore if no combat
        if (combatEntry == null || combatEntry.hasFinished()) {
            return;
        }

        // Get command from message
        String[] args = event.getMessage().toLowerCase().split(" ");
        if (args.length <= 0) {
            return;
        }

        String command = args[0].replaceFirst("/", "");

        // Block command directly
        if (blockedCommands.contains(command)) {
            event.getPlayer().sendMessage(plugin.getMessages().getCommandCombatMessage());
            event.setCancelled(true);
            plugin.logger().info("Blocked command on combat: {}", command);
            return;
        }

        PluginCommand pluginCommand = plugin.getServer().getPluginCommand(command);
        if (pluginCommand == null) {
            plugin.logger().warn("Could not check command for combat: {}", command);
            return;
        }

        // Check for its aliases
        for (String alias : pluginCommand.getAliases()) {
            if (alias.equalsIgnoreCase(command)) {
                event.getPlayer().sendMessage(plugin.getMessages().getCommandCombatMessage());
                event.setCancelled(true);
                plugin.logger().info("Blocked command on combat: {} (alias of {})", alias, pluginCommand.getName());
                return;
            }
        }
    }

    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent event) {
        // Assure there is no combat for player
        this.combatEntries.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    private void onPlayerQuit(PlayerQuitEvent event) {
        // Do not remove from the map because we need it on death event below
        CombatEntry combatEntry = this.combatEntries.get(event.getPlayer().getUniqueId());

        // Ignore if no combat
        if (combatEntry == null || combatEntry.hasFinished()) {
            return;
        }

        // If there is a combat that should not kill the player on quit
        if (!combatEntry.getCombatType().shouldKillOnQuit()) {
            // Since there is a combat, end it
            combatEntry.endTicks();

            // We won't be needing it anymore (the player won't be killed)
            this.combatEntries.remove(event.getPlayer().getUniqueId());
            return;
        }

        // If player has active combat, punish by killing it, do not remove from the map because we need to check on
        // death
        event.getPlayer().setHealth(0.0);
    }

    @EventHandler(ignoreCancelled = true)
    private void onPlayerDeath(PlayerDeathEvent event) {
        CombatEntry combatEntry = this.combatEntries.remove(event.getPlayer().getUniqueId());

        if (combatEntry != null) {
            // Only some combat types will not keep inventory, let's exclude this case
            boolean shouldNotKeepInventory =
                !combatEntry.hasFinished() && !combatEntry.getCombatType().shouldKeepInventory();

            // Stop combat bar
            combatEntry.endTicks();

            if (shouldNotKeepInventory) {
                return;
            }
        }

        // If player has active combat that keeps inventory
        event.setKeepInventory(true);
        // Drop experience
        event.setKeepLevel(false);
        event.setShouldDropExperience(true);
        // Do not drop items
        event.getDrops().clear();
    }

    @EventHandler(ignoreCancelled = true)
    private void onPlayerDamagedByEntity(EntityDamageByEntityEvent event) {
        // Ignore non-damage
        if (event.getFinalDamage() <= 0.0) {
            return;
        }

        // Skip if damaged entity is not player
        if (!(event.getEntity() instanceof Player damaged)) {
            return;
        }

        Player damager;
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() != null &&
                projectile.getShooter() instanceof Player player) {
            damager = player;
        } else if (event.getDamager() instanceof Player player) {
            damager = player;
        } else if (event.getDamager() instanceof EnderCrystal) {
            setOnCombat(damaged, CombatType.PLAYER_VERSUS_PLAYER);
            return;
        } else {
            setOnCombat(damaged, CombatType.PLAYER_VERSUS_ENTITY);
            return;
        }

        // Skip combat if player damaged itself (projectiles, ender pearl)
        if (Objects.equals(damaged.getUniqueId(), damager.getUniqueId())) {
            return;
        }

        // Set both on combat
        setOnCombat(damaged, CombatType.PLAYER_VERSUS_PLAYER);
        setOnCombat(damager, CombatType.PLAYER_VERSUS_PLAYER);
    }

    @EventHandler(ignoreCancelled = true)
    private void onPlayerDamaged(EntityDamageEvent event) {
        // Ignore non-damage
        if (event.getFinalDamage() <= 0.0) {
            return;
        }

        // Skip if damaged entity is not player
        if (!(event.getEntity() instanceof Player damaged)) {
            return;
        }

        CombatType combatType = CAUSE_COMBAT_TYPE_MAP.get(event.getCause());
        if (combatType == null) {
            return;
        }

        setOnCombat(damaged, combatType);
    }

    private void setOnCombat(Player player, CombatType combatType) {
        CombatEntry previousCombat = this.combatEntries.get(player.getUniqueId());
        if (previousCombat != null) {
            if (previousCombat.getCombatType().hasHigherPriority(combatType)) {
                // Skip if current combat has higher priority
                return;
            } else if (previousCombat.getCombatType().isSamePriority(combatType)) {
                // Reset time if same priority
                previousCombat.restartTicks();
                return;
            } else {
                // Lower priority: hide current boss bar and let's start another combat (do not return)
                previousCombat.endTicks();
            }
        }

        CombatEntry newCombat = new CombatEntry(player, combatType);
        this.combatEntries.put(player.getUniqueId(), newCombat);
    }

    private class CombatEntry {

        private final Player player;
        private final BossBar bossBar;

        private final CombatType combatType;

        private final int maxTicks;
        private int ticksRemaining;

        CombatEntry(Player player, CombatType combatType) {
            this.player = player;
            this.combatType = combatType;
            this.maxTicks = combatType.getTickDuration();
            this.ticksRemaining = this.maxTicks;

            Component message;
            if (!combatType.shouldKeepInventory()) {
                message = plugin.getMessages().getUnsafeCombatMessage();
            } else if (combatType.shouldKillOnQuit()) {
                message = plugin.getMessages().getDefaultCombatMessage();
            } else {
                message = plugin.getMessages().getSafeCombatMessage();
            }

            this.bossBar = BossBar.bossBar(message, BossBar.MAX_PROGRESS, BossBar.Color.RED, BossBar.Overlay.PROGRESS,
                Set.of(BossBar.Flag.PLAY_BOSS_MUSIC));
            this.player.showBossBar(this.bossBar);
        }

        public CombatType getCombatType() {
            return combatType;
        }

        public void restartTicks() {
            this.ticksRemaining = this.maxTicks;
            this.bossBar.progress(BossBar.MAX_PROGRESS);
            // Show boss bar again if it was hidden previously
            if (hasFinished()) {
                this.player.showBossBar(this.bossBar);
            }
        }

        public void endTicks() {
            ticksRemaining = 0;
            bossBar.progress(BossBar.MIN_PROGRESS);
            player.hideBossBar(bossBar);
        }

        public boolean hasFinished() {
            return this.ticksRemaining <= 0;
        }

        public void tick() {
            this.ticksRemaining -= 1;

            if (hasFinished()) {
                endTicks();
                return;
            }

            this.bossBar.progress((float) ticksRemaining / maxTicks);
        }
    }

    private enum CombatType {

        PLAYER_VERSUS_PLAYER(10, 25, true, false),
        PLAYER_VERSUS_ENTITY(2, 7, true, true),
        SELF_INFLICTED(1, 5, false, true);

        private final int priority;
        private final int tickDuration;
        private final boolean killOnQuit;
        private final boolean keepInventory;

        CombatType(int priority, int combatDurationSeconds, boolean killOnQuit, boolean keepInventory) {
            this.priority = priority;
            this.tickDuration = combatDurationSeconds * TICKS_PER_SECOND;
            this.killOnQuit = killOnQuit;
            this.keepInventory = keepInventory;
        }

        public boolean hasHigherPriority(CombatType other) {
            return this.priority > other.priority;
        }

        public boolean isSamePriority(CombatType other) {
            return this.priority == other.priority;
        }

        public boolean shouldKillOnQuit() {
            return this.killOnQuit;
        }

        public boolean shouldKeepInventory() {
            return this.keepInventory;
        }

        public int getTickDuration() {
            return tickDuration;
        }
    }
}
