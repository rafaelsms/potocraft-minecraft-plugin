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

public class AllowCommand implements CommandExecutor {

    private final PotoCraftPlugin plugin;

    public AllowCommand(PotoCraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
        @NotNull String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            if (!sender.hasPermission(Permission.ALLOW_PLAYER.getPermission())) {
                sender.sendMessage(plugin.getMessages().getNoPermission());
                return true;
            }

            allowPlayer(sender, player.getUniqueId(), args[0]);
            return true;
        } else if (args.length == 2) {
            // Check permissions for adding players to others
            if (!sender.hasPermission(Permission.ALLOW_PLAYER_OTHER.getPermission())) {
                sender.sendMessage(plugin.getMessages().getNoPermission());
                return true;
            }

            // Check allowing player
            OfflinePlayer allowingPlayer = plugin.searchOfflinePlayer(args[0]);
            if (allowingPlayer == null) {
                sender.sendMessage(plugin.getMessages().getPlayerNotFound(args[0]));
                return true;
            }

            allowPlayer(sender, allowingPlayer.getUniqueId(), args[1]);
            return true;
        } else {
            return false;
        }
    }

    private void allowPlayer(CommandSender sender, UUID player, String playerAllowedName) {
        // Checking allowed player
        OfflinePlayer allowedPlayer = plugin.searchOfflinePlayer(playerAllowedName);
        if (allowedPlayer == null) {
            sender.sendMessage(plugin.getMessages().getPlayerNotFound(playerAllowedName));
            return;
        }

        try {
            if (plugin.getPlayerDatabase().addAllowedPlayer(player, allowedPlayer.getUniqueId())) {
                sender.sendMessage(plugin.getMessages().getPlayerAllowed(allowedPlayer.getName()));
            } else {
                sender.sendMessage(plugin.getMessages().getPlayerAlreadyAllowed(allowedPlayer.getName()));
            }
        } catch (ExecutionException e) {
            sender.sendMessage(plugin.getMessages().getDatabaseAccessError());
        }
    }
}
