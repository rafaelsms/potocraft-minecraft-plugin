package com.rafaelsms.potocraft.commands;

import com.rafaelsms.potocraft.Permission;
import com.rafaelsms.potocraft.PotoCraftPlugin;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class DisallowCommand implements CommandExecutor {

    private final PotoCraftPlugin plugin;

    public DisallowCommand(PotoCraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
        @NotNull String[] args) {
        if (args.length == 0 && sender instanceof Player player) {
            if (!sender.hasPermission(Permission.DISALLOW_PLAYER.getPermission())) {
                sender.sendMessage(plugin.getMessages().getNoPermission());
                return true;
            }

            try {
                plugin.getPlayerDatabase().removeAllowedPlayers(player.getUniqueId());
                sender.sendMessage(plugin.getMessages().getPlayersNotAllowed());
            } catch (ExecutionException e) {
                sender.sendMessage(plugin.getMessages().getDatabaseAccessError());
            }
            return true;
        } else if (args.length == 1 && sender instanceof Player player) {
            if (!sender.hasPermission(Permission.DISALLOW_PLAYER.getPermission())) {
                sender.sendMessage(plugin.getMessages().getNoPermission());
                return true;
            }

            disallowPlayer(sender, player.getUniqueId(), args[0]);
            return true;
        } else if (args.length == 2) {
            // Check permissions for removing players from others
            if (!sender.hasPermission(Permission.DISALLOW_PLAYER_OTHER.getPermission())) {
                sender.sendMessage(plugin.getMessages().getNoPermission());
                return true;
            }

            // Check disallowing player
            OfflinePlayer disallowingPlayer = plugin.searchOfflinePlayer(args[0]);
            if (disallowingPlayer == null) {
                sender.sendMessage(plugin.getMessages().getPlayerNotFound(args[0]));
                return true;
            }

            disallowPlayer(sender, disallowingPlayer.getUniqueId(), args[1]);
            return true;
        } else {
            sender.sendMessage(plugin.getMessages().getDisallowCommandHelp());
            return true;
        }
    }

    private void disallowPlayer(CommandSender sender, UUID player, String playerDisallowedName) {
        // Checking allowed player
        OfflinePlayer disallowedPlayer = plugin.searchOfflinePlayer(playerDisallowedName);
        if (disallowedPlayer == null) {
            sender.sendMessage(plugin.getMessages().getPlayerNotFound(playerDisallowedName));
            return;
        }

        try {
            if (plugin.getPlayerDatabase().removeAllowedPlayer(player, disallowedPlayer.getUniqueId())) {
                sender.sendMessage(plugin.getMessages().getPlayerNotAllowed(disallowedPlayer.getName()));
            } else {
                sender.sendMessage(plugin.getMessages().getPlayerAlreadyNotAllowed(disallowedPlayer.getName()));
            }
        } catch (ExecutionException e) {
            sender.sendMessage(plugin.getMessages().getDatabaseAccessError());
        }
    }
}
