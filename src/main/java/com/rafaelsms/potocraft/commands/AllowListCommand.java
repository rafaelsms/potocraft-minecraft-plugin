package com.rafaelsms.potocraft.commands;

import com.rafaelsms.potocraft.Permission;
import com.rafaelsms.potocraft.PotoCraftPlugin;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class AllowListCommand implements CommandExecutor {

    private final PotoCraftPlugin plugin;

    public AllowListCommand(PotoCraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
        @NotNull String[] args) {
        if (args.length == 0 && sender instanceof Player player) {
            if (!sender.hasPermission(Permission.LIST_PLAYERS_ALLOWED.getPermission())) {
                sender.sendMessage(plugin.getMessages().getNoPermission());
                return true;
            }

            showPlayerList(sender, player.getUniqueId());
            return true;
        } else if (args.length == 1) {
            if (!sender.hasPermission(Permission.LIST_PLAYERS_ALLOWED_OTHER.getPermission())) {
                sender.sendMessage(plugin.getMessages().getNoPermission());
                return true;
            }

            OfflinePlayer offlinePlayer = plugin.searchOfflinePlayer(args[0]);
            if (offlinePlayer == null) {
                sender.sendMessage(plugin.getMessages().getPlayerNotFound(args[0]));
                return true;
            }

            showPlayerList(sender, offlinePlayer.getUniqueId());
            return true;
        } else {
            sender.sendMessage(plugin.getMessages().getAllowListCommandHelp());
            return true;
        }
    }

    private void showPlayerList(CommandSender sender, UUID playerId) {
        try {
            List<UUID> playersAllowed = plugin.getPlayerDatabase().getPlayersAllowed(playerId);

            List<String> playerNames = new ArrayList<>(playersAllowed.size());
            for (UUID player : playersAllowed) {
                OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(player);
                if (offlinePlayer.getName() != null) {
                    playerNames.add(offlinePlayer.getName());
                } else {
                    playerNames.add("(desconhecido)");
                }
            }

            sender.sendMessage(plugin.getMessages().getPlayersAllowed(playerNames));
        } catch (ExecutionException e) {
            sender.sendMessage(plugin.getMessages().getDatabaseAccessError());
        }
    }
}
