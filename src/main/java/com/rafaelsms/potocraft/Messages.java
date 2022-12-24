package com.rafaelsms.potocraft;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Messages {

    private final JavaPlugin plugin;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Component getNoPermission() {
        return plugin.getServer().permissionMessage();
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

    public Component getPlayersAllowed(List<String> playerList) {
        TagResolver.Single playerListTag = Placeholder.component("player_list", getPlayerList(playerList));
        return parse("<yellow>Jogadores permitidos: <player_list>", playerListTag);
    }

    private Component getPlayerList(List<String> playerList) {
        List<Component> entries = new ArrayList<>(playerList.size());
        for (String playerName : playerList) {
            TagResolver.Single playerNameTag = Placeholder.unparsed("player_name", playerName);
            entries.add(parse("<gold><player_name></gold>", playerNameTag));
        }
        return Component.join(JoinConfiguration.separator(parse("<yellow>, </yellow>")), entries);
    }

    public Component getPlayerAllowed(String playerName) {
        TagResolver.Single player = Placeholder.unparsed("player_name", playerName);
        return parse("<yellow>Jogador <gold><player_name></gold> está permitido!", player);
    }

    public Component getPlayerAlreadyAllowed(String playerName) {
        TagResolver.Single player = Placeholder.unparsed("player_name", playerName);
        return parse("<red>Jogador <dark_red><player_name></dark_red> já está permitido!", player);
    }

    public Component getPlayerAlreadyNotAllowed(String playerName) {
        TagResolver.Single player = Placeholder.unparsed("player_name", playerName);
        return parse("<red>Jogador <dark_red><player_name></dark_red> já não está permitido!", player);
    }

    public Component getPlayerNotAllowed(String playerName) {
        TagResolver.Single player = Placeholder.unparsed("player_name", playerName);
        return parse("<yellow>Jogador <gold><player_name></gold> foi removido da lista!", player);
    }

    public Component getPlayersNotAllowed() {
        return parse("<yellow>Todos os jogadores foram removidos da lista!");
    }

    public Component getPlayerNotFound(String playerName) {
        TagResolver.Single player = Placeholder.unparsed("player_name", playerName);
        return parse("<red>Jogador <dark_red><player_name></dark_red> não encontrado!", player);
    }

    public Component getDatabaseAccessError() {
        return parse("<red>Falha ao acessar banco de dados!");
    }

    public Component getAllowCommandHelp() {
        return parse("<gold>Para permitir que pessoas quebrem/coloquem blocos juntos ao seus: <yellow>/allow (nome)");
    }

    public Component getAllowListCommandHelp() {
        return parse("<gold>Para listar que pessoas podem quebrar/colocar blocos juntos ao seus: <yellow>/allowlist");
    }

    public Component getDisallowCommandHelp() {
        return parse("<gold>Para proibir que pessoas quebrem/coloquem blocos juntos ao seus: <yellow>/disallow (nome)");
    }

    public Component getUnsafeCombatMessage() {
        return parse("<dark_red>Você está em combate! <red>Perderá os itens se morrer ou sair!");
    }

    public Component getDefaultCombatMessage() {
        return parse("<red>Você está em combate! Perderá experiência se morrer ou sair!");
    }

    public Component getSafeCombatMessage() {
        return parse("<red>Você está em combate!");
    }

    private static <T> T getOr(T t, T fallback) {
        return Objects.requireNonNullElse(t, fallback);
    }

    private static Component parse(String baseString, TagResolver... tagResolvers) {
        return MiniMessage.miniMessage().deserialize(baseString, tagResolvers);
    }
}
