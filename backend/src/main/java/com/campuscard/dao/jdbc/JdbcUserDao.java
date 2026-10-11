package com.campuscard.dao.jdbc;

import com.campuscard.dao.UserAccount;
import com.campuscard.dao.UserDao;
import com.campuscard.dto.Page;
import com.campuscard.dto.UpdateUserRequest;
import com.campuscard.dto.UserFilter;
import com.campuscard.exception.BusinessException;
import com.campuscard.model.User;

import java.sql.*;
import java.util.*;

public final class JdbcUserDao implements UserDao {
    private static final String COLUMNS = "id, username, password_hash, full_name, student_no, role, enabled, created_at";

    @Override public Optional<UserAccount> findByUsername(Connection c, String username) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM users WHERE username=?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(JdbcMappings.account(rs)) : Optional.empty(); }
        }
    }

    @Override public Optional<UserAccount> findById(Connection c, long id, boolean lock) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM users WHERE id=?" + (lock ? " FOR UPDATE" : ""))) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(JdbcMappings.account(rs)) : Optional.empty(); }
        }
    }

    @Override public User insert(Connection c, String username, String passwordHash, String fullName, String studentNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO users(username,password_hash,full_name,student_no,role,enabled) VALUES(?,?,?,?,'USER',TRUE)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username); ps.setString(2, passwordHash); ps.setString(3, fullName); ps.setString(4, studentNo);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("数据库未返回用户编号");
                return findById(c, keys.getLong(1), false).map(UserAccount::user).orElseThrow(() -> new SQLException("无法读取新建用户"));
            }
        }
    }

    @Override public User update(Connection c, UpdateUserRequest request) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE users SET full_name=?,student_no=? WHERE id=? AND role='USER'")) {
            ps.setString(1, request.fullName()); ps.setString(2, request.studentNo()); ps.setLong(3, request.userId());
            if (ps.executeUpdate() == 0 && !isUser(c, request.userId())) throw new BusinessException("NOT_FOUND", "用户不存在或不可修改");
        }
        return findById(c, request.userId(), false).map(UserAccount::user).orElseThrow(() -> new BusinessException("NOT_FOUND", "用户不存在"));
    }

    @Override public void setEnabled(Connection c, long id, boolean enabled) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE users SET enabled=? WHERE id=? AND role='USER'")) {
            ps.setBoolean(1, enabled); ps.setLong(2, id);
            if (ps.executeUpdate() == 0 && !isUser(c, id)) throw new BusinessException("NOT_FOUND", "用户不存在或不可修改");
        }
    }

    @Override public void updatePassword(Connection c, long id, String passwordHash) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE users SET password_hash=? WHERE id=?")) {
            ps.setString(1, passwordHash); ps.setLong(2, id);
            if (ps.executeUpdate() == 0) throw new BusinessException("NOT_FOUND", "用户不存在");
        }
    }

    @Override public Page<User> query(Connection c, UserFilter filter) throws SQLException {
        StringBuilder where = new StringBuilder(" WHERE role='USER'");
        List<Object> args = new ArrayList<>();
        if (filter.keyword() != null && !filter.keyword().isBlank()) {
            where.append(" AND (username LIKE ? OR full_name LIKE ? OR student_no LIKE ?)");
            String q = "%" + filter.keyword().trim() + "%"; args.add(q); args.add(q); args.add(q);
        }
        if (filter.enabled() != null) { where.append(" AND enabled=?"); args.add(filter.enabled()); }
        long total;
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM users" + where)) {
            bind(ps, args); try (ResultSet rs = ps.executeQuery()) { rs.next(); total = rs.getLong(1); }
        }
        int page = Math.max(filter.page(), 1), size = Math.min(Math.max(filter.pageSize(), 1), 100);
        List<User> users = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id,username,full_name,student_no,role,enabled,created_at FROM users" + where + " ORDER BY id LIMIT ? OFFSET ?")) {
            bind(ps, args); ps.setInt(args.size() + 1, size); ps.setLong(args.size() + 2, (long)(page - 1) * size);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) users.add(JdbcMappings.user(rs)); }
        }
        return new Page<>(users, total, page, size);
    }

    private static void bind(PreparedStatement ps, List<Object> args) throws SQLException {
        for (int i = 0; i < args.size(); i++) {
            Object value = args.get(i); if (value instanceof Boolean b) ps.setBoolean(i + 1, b); else ps.setString(i + 1, (String)value);
        }
    }

    private static boolean isUser(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE id=? AND role='USER'")) {
            ps.setLong(1, id); try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }
}
