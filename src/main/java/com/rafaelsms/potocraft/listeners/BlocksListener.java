package com.rafaelsms.potocraft.listeners;

import com.rafaelsms.potocraft.Permission;
import com.rafaelsms.potocraft.PotoCraftPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class BlocksListener implements Listener {

    private static final Set<EntityType> IGNORED_ENTITIES =
        Set.of(EntityType.BEE, EntityType.TURTLE, EntityType.VILLAGER, EntityType.FALLING_BLOCK);
    private static final Set<Material> INVALID_PISTON_MATERIALS =
        Set.of(Material.PISTON, Material.PISTON_HEAD, Material.MOVING_PISTON, Material.STICKY_PISTON);

    private final PotoCraftPlugin plugin;

    public BlocksListener(PotoCraftPlugin plugin) {
        this.plugin = plugin;
    }

    private enum AttemptType {
        NATURAL,
        READ,
        WRITE
    }

    private void handleBlockAttempt(Player player, Location location, Cancellable cancellable,
        AttemptType attemptType) {
        // Check if player can bypass protection
        if (player != null && player.hasPermission(Permission.OVERRIDE_PROTECTION.getPermission())) {
            return;
        }

        try {
            // Get player id if given
            UUID playerId;
            if (player != null) {
                playerId = player.getUniqueId();
            } else {
                playerId = null;
            }

            // Check database for permissions
            Optional<UUID> blockOwner;
            switch (attemptType) {
                case NATURAL -> blockOwner = plugin.getBlockDatabase().getBlockOwnerToNaturalAction(location);
                case READ -> blockOwner = plugin.getBlockDatabase().getBlockOwnerToRead(playerId, location);
                case WRITE -> blockOwner = plugin.getBlockDatabase().getBlockOwnerToWrite(playerId, location);
                default -> throw new IllegalStateException("Unhandled protection check type!");
            }

            // If there is a protected block owner, deny event
            if (blockOwner.isPresent()) {
                if (player != null) {
                    player.sendActionBar(plugin.getMessages().getBlockNearbyHasOwner(blockOwner.get()));
                }
                cancellable.setCancelled(true);
            }
        } catch (ExecutionException e) {
            // If failed, send warning and deny event
            if (player != null) {
                player.sendActionBar(plugin.getMessages().getDatabaseAccessError());
            }
            cancellable.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    private void onBlockBreakAttempt(BlockBreakEvent event) {
        handleBlockAttempt(event.getPlayer(), event.getBlock().getLocation(), event, AttemptType.READ);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onBlockBreak(BlockBreakEvent event) {
        try {
            plugin.getBlockDatabase().removeBlock(event.getBlock().getLocation());
        } catch (ExecutionException e) {
            event.getPlayer().sendActionBar(plugin.getMessages().getDatabaseAccessError());
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onBlockExplode(BlockExplodeEvent event) {
        try {
            plugin.getBlockDatabase().removeBlocks(mapToLocations(event.blockList()));
        } catch (ExecutionException e) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    private void onBlockPlaceAttempt(BlockPlaceEvent event) {
        handleBlockAttempt(event.getPlayer(), event.getBlock().getLocation(), event, AttemptType.WRITE);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onBlockPlace(BlockPlaceEvent event) {
        // Do not allow unsafe blocks to be placed
        if (event.getPlayer().hasPermission(Permission.OVERRIDE_PROTECTION.getPermission())) {
            event.getPlayer().sendActionBar(plugin.getMessages().getUnsafePermissionSet());
            return;
        }

        try {
            UUID playerId = event.getPlayer().getUniqueId();
            Location location = event.getBlock().getLocation();
            plugin.getBlockDatabase().addProtectedBlock(playerId, location);
        } catch (ExecutionException e) {
            event.getPlayer().sendActionBar(plugin.getMessages().getDatabaseAccessError());
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    private void onEndCrystalInteract(PlayerInteractEvent event) {
        Location location = event.getInteractionPoint();
        ItemStack item = event.getItem();
        if (item == null || location == null || item.getType() != Material.END_CRYSTAL) {
            return;
        }

        handleBlockAttempt(event.getPlayer(), location, event, AttemptType.WRITE);
    }

    @EventHandler(ignoreCancelled = true)
    private void onLavaBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getBucket() != Material.LAVA_BUCKET) {
            return;
        }

        event.getPlayer().sendActionBar(plugin.getMessages().getProhibitedLavaCasts());
    }

    @EventHandler(ignoreCancelled = true)
    private void onBucketEmpty(PlayerBucketEmptyEvent event) {
        handleBlockAttempt(event.getPlayer(), event.getBlock().getLocation(), event, AttemptType.WRITE);
    }

    @EventHandler(ignoreCancelled = true)
    private void onBucketFill(PlayerBucketFillEvent event) {
        handleBlockAttempt(event.getPlayer(), event.getBlock().getLocation(), event, AttemptType.READ);
    }

    @EventHandler(ignoreCancelled = true)
    private void onBucketFill(PlayerBucketEntityEvent event) {
        handleBlockAttempt(event.getPlayer(), event.getEntity().getLocation(), event, AttemptType.READ);
    }

    @EventHandler(ignoreCancelled = true)
    private void onBlockBurnAttempt(BlockBurnEvent event) {
        handleBlockAttempt(null, event.getBlock().getLocation(), event, AttemptType.NATURAL);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onBlockBurn(BlockBurnEvent event) {
        try {
            plugin.getBlockDatabase().removeBlock(event.getBlock().getLocation());
        } catch (ExecutionException e) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    private void onFireSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() != Material.FIRE) {
            return;
        }

        handleBlockAttempt(null, event.getBlock().getLocation(), event, AttemptType.NATURAL);

        // Remove fire source
        if (event.isCancelled()) {
            event.getSource().setType(Material.AIR);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onLiquidFlow(BlockFromToEvent event) {
        // Ignore non-liquids
        if (!event.getBlock().isLiquid()) {
            return;
        }

        handleBlockAttempt(null, event.getBlock().getLocation(), event, AttemptType.NATURAL);
    }

    @EventHandler(ignoreCancelled = true)
    private void onInventoryInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        if (block.getState() instanceof InventoryHolder) {
            Player player = event.getPlayer();
            try {
                Optional<UUID> blockOwner =
                    plugin.getBlockDatabase().getBlockOwnerToRead(player.getUniqueId(), block.getLocation());

                if (blockOwner.isPresent()) {
                    player.sendActionBar(plugin.getMessages().getBlockNearbyHasOwner(blockOwner.get()));
                    event.setUseInteractedBlock(Event.Result.DENY);
                }
            } catch (ExecutionException e) {
                player.sendActionBar(plugin.getMessages().getDatabaseAccessError());
                event.setUseInteractedBlock(Event.Result.DENY);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    private void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        handleBlockAttempt(player, event.getEntity().getLocation(), event, AttemptType.READ);
    }

    @EventHandler(ignoreCancelled = true)
    private void onPortalCreation(PortalCreateEvent event) {
        Location location = getAverageLocation(event.getBlocks());

        Player player;
        if (event.getEntity() instanceof Player player_) {
            player = player_;
        } else {
            player = null;
        }

        handleBlockAttempt(player, location, event, AttemptType.READ);
    }

    private static Location getAverageLocation(List<BlockState> blocks) {
        World world = null;
        double sumX = 0, sumY = 0, sumZ = 0;

        for (BlockState block : blocks) {
            if (world != null && world.getUID() != block.getWorld().getUID()) {
                throw new IllegalStateException("Comparing location with different worlds!");
            }
            world = block.getWorld();

            sumX += block.getLocation().getBlockX();
            sumY += block.getLocation().getBlockY();
            sumZ += block.getLocation().getBlockZ();
        }

        return new Location(world, sumX / blocks.size(), sumY / blocks.size(), sumZ / blocks.size());
    }

    @EventHandler(ignoreCancelled = true)
    private void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (IGNORED_ENTITIES.contains(event.getEntityType())) {
            return;
        }

        // Ignore non-solid blocks
        if (!event.getBlock().isSolid() || !event.getTo().isSolid()) {
            return;
        }

        Player player;
        if (event.getEntity() instanceof Player player_) {
            player = player_;
        } else {
            player = null;
        }

        handleBlockAttempt(player, event.getBlock().getLocation(), event, AttemptType.WRITE);
    }

    @EventHandler(ignoreCancelled = true)
    private void onBedEnter(PlayerBedEnterEvent event) {
        handleBlockAttempt(event.getPlayer(), event.getBed().getLocation(), event, AttemptType.READ);
    }

    @EventHandler(ignoreCancelled = true)
    private void onPistonRetractAttempt(BlockPistonRetractEvent event) {
        if (containsInvalidPistonBlocks(event.getBlocks())) {
            event.getBlock().breakNaturally(true);
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    private void onPistonExtendAttempt(BlockPistonExtendEvent event) {
        if (containsInvalidPistonBlocks(event.getBlocks())) {
            event.getBlock().breakNaturally(true);
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onPistonRetract(BlockPistonRetractEvent event) {
        try {
            plugin.getBlockDatabase().removeBlocks(mapToLocations(event.getBlocks()));
        } catch (ExecutionException e) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onPistonExtend(BlockPistonExtendEvent event) {
        try {
            plugin.getBlockDatabase().removeBlocks(mapToLocations(event.getBlocks()));
        } catch (ExecutionException e) {
            event.setCancelled(true);
        }
    }

    private static List<Location> mapToLocations(List<Block> blocks) {
        return blocks.stream().map(Block::getLocation).toList();
    }

    private static boolean containsInvalidPistonBlocks(List<Block> blocks) {
        for (Block block : blocks) {
            // Prevent recursive/moving contraptions
            if (INVALID_PISTON_MATERIALS.contains(block.getType())) {
                return true;
            }
        }

        return false;
    }
}
