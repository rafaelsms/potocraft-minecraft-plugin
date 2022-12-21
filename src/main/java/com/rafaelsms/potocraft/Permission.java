package com.rafaelsms.potocraft;

import org.bukkit.plugin.java.JavaPlugin;

public enum Permission {

    OVERRIDE_PROTECTION("potocraft.override_protection");

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
