package com.bitraid.authBit;

import at.favre.lib.crypto.bcrypt.BCrypt;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    private Connection connection;
    private final AuthBit plugin;

    public Database(AuthBit plugin) {
        this.plugin = plugin;
    }

    private void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS users (" +
                "username VARCHAR PRIMARY KEY  NOT NULL UNIQUE," +
                "password_hash VARCHAR NOT NULL," +
                "is_premium BOOLEAN NOT NULL DEFAULT 0" +
                ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    public void connect() throws SQLException {
        if (connection != null && !connection.isClosed()) return;


        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        File dbFile = new File(plugin.getDataFolder(), "database.db");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        // Conectamos
        connection = DriverManager.getConnection(url);

        createTable();
    }

    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    // Registrar usuario con hasheando su contraseña
    public void registerUser(String username, String rawPassword) throws SQLException {
        // Encriptamos la contraseña con un factor de costo de 12
        String bcryptHashString = BCrypt.withDefaults().hashToString(12, rawPassword.toCharArray());

        String sql = "INSERT INTO users (username, password_hash, is_premium) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username.toLowerCase()); // Guardamos en minúsculas por seguridad
            pstmt.setString(2, bcryptHashString);
            pstmt.setBoolean(3, false);
            pstmt.executeUpdate();
        }
    }

    // 3. Verificar login
    public boolean checkLogin(String username, String rawPassword) throws SQLException {
        String sql = "SELECT password_hash FROM users WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username.toLowerCase());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String hash = rs.getString("password_hash");
                // Comparamos la contraseña plana con el hash de la base de datos
                BCrypt.Result result = BCrypt.verifyer().verify(rawPassword.toCharArray(), hash);
                return result.verified;
            }
        }
        return false; // El usuario no existe
    }

    // 4. Utilidades para el modo Premium
    public boolean isUserRegistered(String username) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username.toLowerCase());
            return pstmt.executeQuery().next();
        }
    }

    public boolean isPremium(String username) throws SQLException {
        String sql = "SELECT is_premium FROM users WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username.toLowerCase());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("is_premium");
            }
        }
        return false;
    }

    public void setPremium(String username) throws SQLException {
        String sql = "UPDATE users SET is_premium = ? WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setBoolean(1, true);
            pstmt.setString(2, username.toLowerCase());
            pstmt.executeUpdate();
        }
    }

    public void deleteUser(String username) throws SQLException {
        String sql = "DELETE FROM users WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username.toLowerCase());
            pstmt.executeUpdate();
        }
    }
}