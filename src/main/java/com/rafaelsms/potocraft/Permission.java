package com.rafaelsms.potocraft;

import org.bukkit.plugin.java.JavaPlugin;

public enum Permission {

    OVERRIDE_PROTECTION("potocraft.override_protection"),
    ALLOW_PLAYER("potocraft.commands.allow"),
    ALLOW_PLAYER_OTHER("potocraft.commands.allow.other"),
    DISALLOW_PLAYER("potocraft.commands.disallow"),
    DISALLOW_PLAYER_OTHER("potocraft.commands.disallow.other"),
    LIST_PLAYERS_ALLOWED("potocraft.commands.list"),
    LIST_PLAYERS_ALLOWED_OTHER("potocraft.commands.list.other");

    private final org.bukkit.permissions.Permission permission;

    Permission(String permission) {
        this.permission = new org.bukkit.permissions.Permission(permission);
    }

    public org.bukkit.permissions.Permission getPermission() {
        return permission;
    }

    public static void registerPermissions(JavaPlugin plugin) {
        for (Permission permission : values()) {
            try {
                plugin.getServer().getPluginManager().addPermission(permission.permission);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
