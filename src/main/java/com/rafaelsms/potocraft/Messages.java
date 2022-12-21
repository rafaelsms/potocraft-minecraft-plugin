package com.rafaelsms.potocraft;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;

public class Messages {

    private final JavaPlugin plugin;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Component getBlockNearbyHasOwner(UUID owner) {
        String ownerName = getOr(plugin.getServer().getOfflinePlayer(owner).getName(), "(desconhecido)");
        TagResolver.Single ownerNameResolver = Placeholder.unparsed("owner", ownerName);
        return parse("<red>Bloco próximo possui dono: <dark_red><owner>", ownerNameResolver);
    }

    public Component getUnsafePermissionSet() {
        return parse("<red>Permissão de sobrescrita: o bloco não será protegido");
    }

    public Component getProhibitedLavaCasts() {
        return parse("<red>Note: <i>lavacast</i> é proíbido");
    }

    public Component getDatabaseAccessError() {
        return parse("<red>Falha ao acessar banco de dados!");
    }

    private static <T> T getOr(T t, T fallback) {
        return Objects.requireNonNullElse(t, fallback);
    }

    private static Component parse(String baseString, TagResolver... tagResolvers) {
        return MiniMessage.miniMessage().deserialize(baseString, tagResolvers);
    }
}
