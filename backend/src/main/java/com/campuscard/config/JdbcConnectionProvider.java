package com.campuscard.config;

import com.campuscard.dao.ConnectionProvider;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class JdbcConnectionProvider implements ConnectionProvider {
    private final DatabaseConfig config;

    public JdbcConnectionProvider(DatabaseConfig config) { this.config = config; }

    @Override public Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(config.url(), config.user(), config.password());
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET time_zone = '+08:00'");
            return connection;
        } catch (SQLException e) {
            try { connection.close(); } catch (SQLException closeError) { e.addSuppressed(closeError); }
            throw e;
        }
    }
}
