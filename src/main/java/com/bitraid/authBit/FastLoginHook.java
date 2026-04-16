package com.bitraid.authBit;

import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import org.bukkit.entity.Player;

// Especificamos que manejamos objetos de tipo Player
public class FastLoginHook implements AuthPlugin<Player> {

    private final AuthBit plugin;

    public FastLoginHook(AuthBit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean forceLogin(Player player) {
        // El método sigue igual, solo confirmamos que el objeto es Player
        return plugin.forceLogin(player.getName());
    }

    @Override
    public boolean forceRegister(Player player, String password) {
        return plugin.forceRegister(player.getName(), password);
    }

    @Override
    public boolean isRegistered(String playerName) {
        // --- LA MAGIA ESTÁ AQUÍ ---
        // Solo le decimos a FastLogin que intercepte la conexión si el jugador
        // ya está marcado como PREMIUM en nuestra base de datos AuthBit.
        return plugin.isPremium(playerName);
    }
}