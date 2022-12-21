package com.rafaelsms.potocraft.commands;

import com.rafaelsms.potocraft.PotoCraftPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class AllowCommand implements CommandExecutor {

    private final PotoCraftPlugin plugin;

    public AllowCommand(PotoCraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
        @NotNull String[] args) {
        return false;
    }
}
