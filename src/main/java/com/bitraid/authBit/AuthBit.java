package com.bitraid.authBit;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Filter;
import java.util.logging.LogRecord;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;

public final class AuthBit extends JavaPlugin implements Listener {

    private Database db;
    private final Set<String> loggedInPlayers = new HashSet<>();
    private File messagesFile;
    private FileConfiguration messagesConfig;
    // --- MEMORIA DE SESIONES (SESSION LOCK) ---
    private static class SessionData {
        String ip;
        long timestamp;
        SessionData(String ip, long timestamp) {
            this.ip = ip;
            this.timestamp = timestamp;
        }
    }
    private final Map<String, SessionData> sessionCache = new HashMap<>();

    @Override
    public void onEnable() {
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();
        createMessagesConfig();
        db = new Database(this);
        try {
            db.connect();
        } catch (SQLException e) {
            e.printStackTrace();
            Bukkit.getLogger().severe("¡Error fatal al conectar con la base de datos!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getLogger().setFilter(new Filter() {
            @Override
            public boolean isLoggable(LogRecord record) {
                String msg = record.getMessage();
                return !(msg.contains("/login") || msg.contains("/register") ||
                        msg.contains("/premium") || msg.contains("/unregister"));
            }
        });

        try {
            Logger rootLogger = (Logger) LogManager.getRootLogger();
            rootLogger.addFilter(new AbstractFilter() {
                @Override
                public Result filter(LogEvent event) {
                    if (event == null || event.getMessage() == null) return Result.NEUTRAL;

                    // Convertimos a minúsculas para atrapar variaciones como /ReGiStEr
                    String msg = event.getMessage().getFormattedMessage().toLowerCase();

                    // Filtramos si detectamos que el comando se ejecutó
                    if (msg.contains("issued server command: /register") ||
                            msg.contains("issued server command: /login") ||
                            msg.contains("issued server command: /premium") ||
                            msg.contains("issued server command: /unregister")) {
                        return Result.DENY; // Bloquea el mensaje en la consola
                    }

                    return Result.NEUTRAL; // Deja pasar el resto de mensajes
                }
            });
        } catch (Exception e) {
            Bukkit.getLogger().warning("No se pudo inyectar el filtro de Log4j2 para ocultar contraseñas en consola.");
        }

        Bukkit.getPluginManager().registerEvents(this, this);

        if (getCommand("premium") != null) getCommand("premium").setExecutor(this);
        if (getCommand("register") != null) getCommand("register").setExecutor(this);
        if (getCommand("login") != null) getCommand("login").setExecutor(this);

        if (Bukkit.getPluginManager().isPluginEnabled("FastLogin")) {
            setupFastLogin();
        }
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
    }

    private void createMessagesConfig() {
        messagesFile = new File(getDataFolder(), "messages.yml");

        // Si el archivo no existe en la carpeta del servidor, lo copia desde tu plugin
        if (!messagesFile.exists()) {
            messagesFile.getParentFile().mkdirs();
            saveResource("messages.yml", false);
        }

        // Carga la configuración desde el archivo físico
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }
    private void setupFastLogin() {
        try {
            FastLoginBukkit fastLogin = JavaPlugin.getPlugin(FastLoginBukkit.class);
            fastLogin.getCore().setAuthPluginHook(new FastLoginHook(this));
        } catch (Exception ignored) {}
    }

    @Override
    public void onDisable() {
        try {
            db.close();
        } catch (SQLException ignored) {}
    }

    public boolean forceLogin(String username) {
        loggedInPlayers.add(username);
        Player p = Bukkit.getPlayerExact(username);
        if (p != null) {
            p.sendMessage("§fIniciaste sesión automaticamente en modo §b§lPREMIUM.");
            // Redirigir al lobby
            sendToServer(p, "lobby");
        }
        return true;
    }

    // Creamos el metodo para poder usar messages.yml y darle la opcion de personaizar al usuario
    public String getMessage(String path) {
        // Obtenemos el prefijo y el mensaje. Si no existe, damos un valor por defecto.
        String prefix = messagesConfig.getString("mensajes.prefix", "&8[&6AuthBit&8] ");
        String msg = messagesConfig.getString("mensajes." + path, "&cMensaje no encontrado: " + path);

        return ChatColor.translateAlternateColorCodes('&', prefix + msg);
    }

    public boolean forceRegister(String username, String password) {
        try {
            db.registerUser(username, password);
            return forceLogin(username);
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean isRegistered(String username) {
        try {
            return db.isUserRegistered(username);
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean isPremium(String username) {
        try {
            return db.isPremium(username);
        } catch (SQLException e) {
            return false;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();
        event.setJoinMessage(null);
        Bukkit.broadcastMessage(getMessage("player-join") + player.getName());

        if (!loggedInPlayers.contains(name)) {
            if (sessionCache.containsKey(name) && player.getAddress() != null) {
                SessionData data = sessionCache.get(name);
                String currentIp = player.getAddress().getAddress().getHostAddress();

                // - Verificar que se encuentre en el rango del tiempo permitido -
                if (System.currentTimeMillis() - data.timestamp <= 600000L && currentIp.equals(data.ip)) {
                    loggedInPlayers.add(name);
                    player.sendMessage(getMessage("continued-login-ip"));

                    // Enviar por defecto - FALTA CORREGIR CONFIG.YML
                    sendToServer(player, "lobby");
                    return;
                } else {
                    sessionCache.remove(name);
                }
            }

            try {
                // - Consultar si el usuario es Premium para que FastLogin inicie el inicio de sesión premium -
                if (db.isPremium(name)) {
                    return;
                }

                if (db.isUserRegistered(name)) {
                    player.sendMessage(getMessage("login-message"));
                } else {
                    player.sendMessage(getMessage("register-message"));
                }
            } catch (SQLException ignored) {}
        }
    }
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String name = event.getPlayer().getName();
        event.setQuitMessage(null);
        Bukkit.broadcastMessage(getMessage("player-leave") + name);
        // Solo guardamos la IP si el jugador tenía la sesión iniciada correctamente
        if (loggedInPlayers.contains(name) && event.getPlayer().getAddress() != null) {
            String ip = event.getPlayer().getAddress().getAddress().getHostAddress();
            sessionCache.put(name, new SessionData(ip, System.currentTimeMillis()));
        }

        loggedInPlayers.remove(name);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!loggedInPlayers.contains(event.getPlayer().getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!loggedInPlayers.contains(event.getPlayer().getName())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(getMessage("inicia-sesion-primero"));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        String name = player.getName();
        String cmd = command.getName().toLowerCase();

        try {
            switch (cmd) {
                case "register":
                    if (db.isUserRegistered(name)) {
                        player.sendMessage( getMessage("ya-registrado"));
                        return true;
                    }
                    if (args.length != 2 || !args[0].equals(args[1])) {
                        player.sendMessage(getMessage("error-registro"));
                        return true;
                    }
                    db.registerUser(name, args[0]);
                    loggedInPlayers.add(name);
                    player.sendMessage(getMessage("registro-exitoso"));
                    return true;

                case "login":
                    if (!db.isUserRegistered(name)) {
                        player.sendMessage(getMessage("no-registrado"));
                        return true;
                    }
                    if (loggedInPlayers.contains(name)) return true;
                    if (args.length != 1) {
                        player.sendMessage(getMessage("error-login"));
                        return true;
                    }
                    if (db.checkLogin(name, args[0])) {
                        loggedInPlayers.add(name);
                        player.sendMessage(getMessage("login-exitoso"));

                        // Lo enviamos al servidor llamado "lobby" en Velocity
                        sendToServer(player, "lobby");
                    } else {
                        player.sendMessage(getMessage("contrasena-incorrecta"));
                    }
                    return true;

                case "premium":
                    if (!loggedInPlayers.contains(name)) {
                        player.sendMessage(getMessage("inicia-sesion-primero"));
                        return true;
                    }
                    if (db.isPremium(name)) {
                        player.sendMessage(getMessage("already-premium"));
                        return true;
                    }
                    db.setPremium(name);
                    if (Bukkit.getPluginManager().isPluginEnabled("FastLogin")) {
                        FastLoginBukkit fastLogin = JavaPlugin.getPlugin(FastLoginBukkit.class);
                        fastLogin.getCore().getPendingConfirms().add(player.getUniqueId());

                        com.github.games647.fastlogin.core.storage.StoredProfile profile =
                                fastLogin.getCore().getStorage().loadProfile(name);
                        if (profile != null) {
                            profile.setPremium(true);
                            fastLogin.getCore().getStorage().save(profile);
                        }
                    }
                    player.kickPlayer(getMessage("premium-activated"));
                    return true;

                case "unregister":
                    if (!player.isOp()) {
                        player.sendMessage(getMessage("sin-permiso"));
                        return true;
                    }
                    if (args.length != 1) {
                        player.sendMessage(getMessage("error-unregister"));
                        return true;
                    }

                    String target = args[0].toLowerCase();
                    try {
                        if (!db.isUserRegistered(target)) {
                            player.sendMessage(getMessage("user-invalid"));
                            return true;
                        }

                        db.deleteUser(target);
                        sessionCache.remove(target); // Evitamos que pueda entrar con la caché

                        Player targetPlayer = Bukkit.getPlayerExact(target);
                        if (targetPlayer != null) {
                            loggedInPlayers.remove(target);
                            targetPlayer.kickPlayer(getMessage("account-reset"));
                        }

                        player.sendMessage("§User " + target + " has been eliminated successfully.");
                    } catch (SQLException e) {
                        player.sendMessage("§cInesperated error in DataBase.");
                    }
                    return true;
            }
        } catch (SQLException ignored) {}
        return false;
    }
    public void sendToServer(Player player, String serverName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(serverName); // Nombre del servidor en velocity.toml (ej: "lobby")
        player.sendPluginMessage(this, "BungeeCord", out.toByteArray());
    }
}