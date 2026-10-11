package com.campuscard.dao.jdbc;

import com.campuscard.dao.UserAccount;
import com.campuscard.model.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

final class JdbcMappings {
    private JdbcMappings() {}
    static User user(ResultSet rs) throws SQLException {
        return new User(rs.getLong("id"), rs.getString("username"), rs.getString("full_name"),
                rs.getString("student_no"), Role.valueOf(rs.getString("role")), rs.getBoolean("enabled"),
                localDateTime(rs, "created_at"));
    }
    static UserAccount account(ResultSet rs) throws SQLException {
        return new UserAccount(user(rs), rs.getString("password_hash"));
    }
    static CampusCard card(ResultSet rs) throws SQLException {
        return new CampusCard(rs.getLong("id"), rs.getLong("user_id"), rs.getString("card_no"), rs.getBigDecimal("balance"));
    }
    static TransactionRecord transaction(ResultSet rs) throws SQLException {
        return new TransactionRecord(rs.getLong("id"), rs.getLong("card_id"), rs.getLong("user_id"),
                rs.getString("username"), rs.getString("full_name"), TransactionType.valueOf(rs.getString("type")),
                rs.getBigDecimal("amount"), rs.getBigDecimal("balance_before"), rs.getBigDecimal("balance_after"),
                rs.getString("description"), rs.getLong("actor_id"), rs.getString("operation_id"), localDateTime(rs, "created_at"));
    }
    private static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class);
    }
}
