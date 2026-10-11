package com.campuscard.dao.jdbc;

import com.campuscard.dao.StatisticsDao;
import com.campuscard.dto.Statistics;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;

public final class JdbcStatisticsDao implements StatisticsDao {
    @Override public Statistics summarize(Connection c, LocalDate from, LocalDate to) throws SQLException {
        long users, enabled;
        BigDecimal balance;
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*),COALESCE(SUM(enabled=TRUE),0) FROM users WHERE role='USER'"); ResultSet rs = ps.executeQuery()) {
            rs.next(); users=rs.getLong(1); enabled=rs.getLong(2);
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(SUM(cc.balance),0.00) FROM campus_cards cc JOIN users u ON u.id=cc.user_id WHERE u.role='USER'"); ResultSet rs = ps.executeQuery()) {
            rs.next(); balance=rs.getBigDecimal(1);
        }
        StringBuilder where = new StringBuilder();
        if (from != null) where.append(" AND tr.created_at>=?");
        if (to != null) where.append(" AND tr.created_at<?");
        String sql="SELECT COALESCE(SUM(CASE WHEN tr.type='RECHARGE' THEN tr.amount ELSE 0 END),0.00),COALESCE(SUM(CASE WHEN tr.type='CONSUMPTION' THEN tr.amount ELSE 0 END),0.00),COUNT(*) FROM transaction_records tr WHERE 1=1"+where;
        try (PreparedStatement ps=c.prepareStatement(sql)) {
            int i=1;
            if(from!=null) ps.setTimestamp(i++,Timestamp.valueOf(from.atStartOfDay()));
            if(to!=null) ps.setTimestamp(i,Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            try(ResultSet rs=ps.executeQuery()) { rs.next(); return new Statistics(users,enabled,balance,rs.getBigDecimal(1),rs.getBigDecimal(2),rs.getLong(3)); }
        }
    }
}
