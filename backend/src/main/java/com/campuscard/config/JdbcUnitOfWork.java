package com.campuscard.config;

import com.campuscard.dao.ConnectionProvider;
import com.campuscard.dao.SqlWork;
import com.campuscard.dao.UnitOfWork;
import com.campuscard.exception.BusinessException;

import java.sql.Connection;
import java.sql.SQLException;

public final class JdbcUnitOfWork implements UnitOfWork {
    private final ConnectionProvider provider;

    public JdbcUnitOfWork(ConnectionProvider provider) { this.provider = provider; }

    @Override public <T> T read(SqlWork<T> work) {
        try (Connection connection = provider.open()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException e) {
                rollback(connection, e);
                throw translate(e);
            } catch (RuntimeException | Error e) {
                rollback(connection, e);
                throw e;
            }
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    @Override public <T> T write(SqlWork<T> work) {
        try (Connection connection = provider.open()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException e) {
                rollback(connection, e);
                throw translate(e);
            } catch (RuntimeException | Error e) {
                rollback(connection, e);
                throw e;
            }
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    private static void rollback(Connection connection, Throwable cause) {
        try { connection.rollback(); } catch (SQLException rollbackError) { cause.addSuppressed(rollbackError); }
    }

    private static BusinessException translate(SQLException e) {
        for (SQLException current = e; current != null; current = current.getNextException()) {
            if ("23505".equals(current.getSQLState()) || current.getErrorCode() == 1062)
                return new BusinessException("DUPLICATE", "数据已存在或违反唯一性约束", e);
        }
        return new BusinessException("DATABASE_ERROR", "数据库操作失败", e);
    }
}
