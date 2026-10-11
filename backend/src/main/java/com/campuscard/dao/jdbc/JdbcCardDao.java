package com.campuscard.dao.jdbc;

import com.campuscard.dao.CardDao;
import com.campuscard.exception.BusinessException;
import com.campuscard.model.CampusCard;

import java.math.BigDecimal;
import java.sql.*;
import java.util.Optional;

public final class JdbcCardDao implements CardDao {
    @Override public Optional<CampusCard> findByUserId(Connection c, long userId, boolean lock) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id,user_id,card_no,balance FROM campus_cards WHERE user_id=?" + (lock ? " FOR UPDATE" : ""))) {
            ps.setLong(1, userId); try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(JdbcMappings.card(rs)) : Optional.empty(); }
        }
    }
    @Override public CampusCard insert(Connection c, long userId, String cardNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO campus_cards(user_id,card_no,balance) VALUES(?,?,0.00)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId); ps.setString(2, cardNo); ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("数据库未返回卡号编号");
                long id = rs.getLong(1);
                try (PreparedStatement find = c.prepareStatement("SELECT id,user_id,card_no,balance FROM campus_cards WHERE id=?")) {
                    find.setLong(1, id); try (ResultSet row = find.executeQuery()) { if (row.next()) return JdbcMappings.card(row); }
                }
            }
        }
        throw new SQLException("无法读取新建校园卡");
    }
    @Override public void updateBalance(Connection c, long cardId, BigDecimal balance) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE campus_cards SET balance=? WHERE id=?")) {
            ps.setBigDecimal(1, balance); ps.setLong(2, cardId);
            if (ps.executeUpdate() == 0) throw new BusinessException("NOT_FOUND", "校园卡不存在");
        }
    }
}
