package com.campuscard.dao.jdbc;

import com.campuscard.dao.TransactionDao;
import com.campuscard.dto.Page;
import com.campuscard.dto.TransactionFilter;
import com.campuscard.model.TransactionRecord;
import com.campuscard.model.TransactionType;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public final class JdbcTransactionDao implements TransactionDao {
    private static final String SELECT = "SELECT tr.id,tr.card_id,cc.user_id,u.username,u.full_name,tr.type,tr.amount,tr.balance_before,tr.balance_after,tr.description,tr.actor_id,tr.operation_id,tr.created_at FROM transaction_records tr JOIN campus_cards cc ON cc.id=tr.card_id JOIN users u ON u.id=cc.user_id";

    @Override public Optional<TransactionRecord> findByOperationId(Connection c, String operationId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + " WHERE tr.operation_id=?")) {
            ps.setString(1, operationId); try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(JdbcMappings.transaction(rs)) : Optional.empty(); }
        }
    }

    @Override public TransactionRecord insert(Connection c, long cardId, TransactionType type, java.math.BigDecimal amount,
                                               java.math.BigDecimal before, java.math.BigDecimal after, String description,
                                               long actorId, String operationId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO transaction_records(card_id,type,amount,balance_before,balance_after,description,actor_id,operation_id) VALUES(?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, cardId); ps.setString(2, type.name()); ps.setBigDecimal(3, amount); ps.setBigDecimal(4, before);
            ps.setBigDecimal(5, after); ps.setString(6, description); ps.setLong(7, actorId); ps.setString(8, operationId); ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("数据库未返回交易编号");
                try (PreparedStatement find = c.prepareStatement(SELECT + " WHERE tr.id=?")) {
                    find.setLong(1, keys.getLong(1)); try (ResultSet rs = find.executeQuery()) { if (rs.next()) return JdbcMappings.transaction(rs); }
                }
            }
        }
        throw new SQLException("无法读取新建交易记录");
    }

    @Override public Page<TransactionRecord> query(Connection c, TransactionFilter filter) throws SQLException {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (filter.userId() != null) { where.append(" AND cc.user_id=?"); args.add(filter.userId()); }
        if (filter.type() != null) { where.append(" AND tr.type=?"); args.add(filter.type().name()); }
        if (filter.from() != null) { where.append(" AND tr.created_at>=?"); args.add(filter.from().atStartOfDay()); }
        if (filter.to() != null) { where.append(" AND tr.created_at<?"); args.add(filter.to().plusDays(1).atStartOfDay()); }
        long total;
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM transaction_records tr JOIN campus_cards cc ON cc.id=tr.card_id" + where)) {
            bind(ps, args); try (ResultSet rs = ps.executeQuery()) { rs.next(); total = rs.getLong(1); }
        }
        int page = Math.max(1, filter.page()), size = Math.min(100, Math.max(1, filter.pageSize()));
        List<TransactionRecord> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT + where + " ORDER BY tr.created_at DESC,tr.id DESC LIMIT ? OFFSET ?")) {
            bind(ps, args); ps.setInt(args.size()+1, size); ps.setLong(args.size()+2, (long)(page-1)*size);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) rows.add(JdbcMappings.transaction(rs)); }
        }
        return new Page<>(rows, total, page, size);
    }

    private static void bind(PreparedStatement ps, List<Object> args) throws SQLException {
        for (int i=0;i<args.size();i++) {
            Object v=args.get(i); int index=i+1;
            if (v instanceof Long n) ps.setLong(index,n);
            else if (v instanceof String s) ps.setString(index,s);
            else ps.setTimestamp(index, Timestamp.valueOf((java.time.LocalDateTime)v));
        }
    }
}
