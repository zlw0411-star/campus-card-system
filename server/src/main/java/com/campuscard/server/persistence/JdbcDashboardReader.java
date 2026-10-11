package com.campuscard.server.persistence;

import com.campuscard.server.contract.DashboardOverview;
import com.campuscard.server.contract.DashboardReader;
import com.campuscard.server.contract.TrendPoint;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JdbcDashboardReader implements DashboardReader {
    @Override public DashboardOverview summarize(Connection connection, Long userId, LocalDate from, LocalDate to) throws SQLException {
        if (from == null || to == null || to.isBefore(from)) throw new IllegalArgumentException("仪表盘日期范围无效");
        StringBuilder sql = new StringBuilder("SELECT DATE(tr.created_at) AS day,")
                .append("COALESCE(SUM(CASE WHEN tr.type='RECHARGE' THEN tr.amount ELSE 0 END),0.00) AS recharge_amount,")
                .append("COALESCE(SUM(CASE WHEN tr.type='CONSUMPTION' THEN tr.amount ELSE 0 END),0.00) AS consumption_amount,")
                .append("COUNT(*) AS transaction_count ")
                .append("FROM transaction_records tr ")
                .append("JOIN campus_cards cc ON cc.id=tr.card_id ")
                .append("JOIN users u ON u.id=cc.user_id ")
                .append("WHERE u.role='USER' AND tr.created_at>=? AND tr.created_at<?");
        if (userId != null) sql.append(" AND u.id=?");
        sql.append(" GROUP BY DATE(tr.created_at) ORDER BY DATE(tr.created_at)");

        Map<LocalDate, DailyTotal> byDay = new HashMap<>();
        BigDecimal recharge = BigDecimal.ZERO.setScale(2);
        BigDecimal consumption = BigDecimal.ZERO.setScale(2);
        long count = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setTimestamp(1, Timestamp.valueOf(from.atStartOfDay()));
            statement.setTimestamp(2, Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            if (userId != null) statement.setLong(3, userId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    LocalDate date = rs.getDate("day").toLocalDate();
                    BigDecimal dayRecharge = rs.getBigDecimal("recharge_amount");
                    BigDecimal dayConsumption = rs.getBigDecimal("consumption_amount");
                    long dayCount = rs.getLong("transaction_count");
                    byDay.put(date, new DailyTotal(dayRecharge, dayConsumption, dayCount));
                    recharge = recharge.add(dayRecharge);
                    consumption = consumption.add(dayConsumption);
                    count += dayCount;
                }
            }
        }

        List<TrendPoint> trend = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            DailyTotal total = byDay.getOrDefault(date, DailyTotal.ZERO);
            trend.add(new TrendPoint(date, total.recharge().toPlainString(), total.consumption().toPlainString()));
        }
        return new DashboardOverview(recharge.toPlainString(), consumption.toPlainString(), count, trend);
    }

    private record DailyTotal(BigDecimal recharge, BigDecimal consumption, long count) {
        private static final DailyTotal ZERO = new DailyTotal(BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), 0);
    }
}
