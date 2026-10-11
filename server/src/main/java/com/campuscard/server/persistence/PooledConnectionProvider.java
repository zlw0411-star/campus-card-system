package com.campuscard.server.persistence;

import com.campuscard.dao.ConnectionProvider;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class PooledConnectionProvider implements ConnectionProvider {
    private final HikariDataSource dataSource;

    PooledConnectionProvider(HikariDataSource dataSource) { this.dataSource = dataSource; }

    @Override public Connection open() throws SQLException {
        Connection connection = dataSource.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET time_zone = '+08:00'");
            return connection;
        } catch (SQLException e) {
            try { connection.close(); } catch (SQLException closeError) { e.addSuppressed(closeError); }
            throw e;
        }
    }
}
