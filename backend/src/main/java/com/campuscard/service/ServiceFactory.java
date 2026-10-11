package com.campuscard.service;

import com.campuscard.api.*;
import com.campuscard.dao.*;
import com.campuscard.dto.*;
import com.campuscard.exception.BusinessException;
import com.campuscard.model.*;
import com.campuscard.security.PasswordHasher;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Builds the application-facing services over a shared unit of work. */
public final class ServiceFactory {
    private ServiceFactory() { }

    public static ApplicationServices create(UnitOfWork unitOfWork, UserDao userDao, CardDao cardDao,
                                             TransactionDao transactionDao, StatisticsDao statisticsDao) {
        Objects.requireNonNull(unitOfWork, "unitOfWork");
        Objects.requireNonNull(userDao, "userDao");
        Objects.requireNonNull(cardDao, "cardDao");
        Objects.requireNonNull(transactionDao, "transactionDao");
        Objects.requireNonNull(statisticsDao, "statisticsDao");
        Services s = new Services(unitOfWork, userDao, cardDao, transactionDao, statisticsDao);
        return new ApplicationServices(new Auth(s), new Cards(s), new Records(s), new Admin(s), new Stats(s));
    }

    private static final class Services {
        final UnitOfWork uow;
        final UserDao users;
        final CardDao cards;
        final TransactionDao transactions;
        final StatisticsDao statistics;
        final Map<String, SessionContext> sessions = new ConcurrentHashMap<>();
        Services(UnitOfWork u, UserDao us, CardDao c, TransactionDao t, StatisticsDao st) {
            uow = u; users = us; cards = c; transactions = t; statistics = st;
        }
    }

    private static final class Principal {
        final User user;
        Principal(User user) { this.user = user; }
        boolean admin() { return user.role() == Role.ADMIN; }
    }

    /** Validate both the unforgeable registered context and the live account on this connection. */
    private static Principal authorize(Services s, Connection c, SessionContext supplied) throws java.sql.SQLException {
        if (supplied == null || supplied.token() == null || supplied.token().isBlank())
            throw error("AUTH_REQUIRED", "请先登录");
        SessionContext registered = s.sessions.get(supplied.token());
        if (registered == null) throw error("AUTH_REQUIRED", "登录已失效，请重新登录");
        if (!registered.equals(supplied)) throw error("FORBIDDEN", "会话信息无效");
        UserAccount account = s.users.findById(c, registered.userId(), false)
                .orElseThrow(() -> error("AUTH_REQUIRED", "登录用户不存在，请重新登录"));
        User user = account.user();
        if (!user.enabled()) throw error("USER_DISABLED", "账户已停用");
        if (user.role() != registered.role()) throw error("FORBIDDEN", "账户权限已变更，请重新登录");
        return new Principal(user);
    }

    private static Principal requireAdmin(Services s, Connection c, SessionContext session) throws java.sql.SQLException {
        Principal p = authorize(s, c, session);
        if (!p.admin()) throw error("FORBIDDEN", "仅管理员可执行此操作");
        return p;
    }

    private static Principal requireAdminForWrite(Services s, Connection c, SessionContext session) throws java.sql.SQLException {
        Principal p = requireAdmin(s, c, session);
        UserAccount locked = s.users.findById(c, p.user.id(), true)
                .orElseThrow(() -> error("AUTH_REQUIRED", "登录用户不存在，请重新登录"));
        if (!locked.user().enabled()) throw error("USER_DISABLED", "账户已停用");
        if (locked.user().role() != Role.ADMIN) throw error("FORBIDDEN", "账户权限已变更，请重新登录");
        return new Principal(locked.user());
    }

    private static BusinessException error(String code, String message) { return new BusinessException(code, message); }
    private static BusinessException validation(String message) { return error("VALIDATION", message); }

    private static void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw validation("分页参数无效");
    }

    private static void validateDates(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) throw validation("结束日期不能早于开始日期");
    }

    private static String requiredText(String value, int max, String name) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty() || v.length() > max) throw validation(name + "长度无效");
        return v;
    }

    private static String username(String value) {
        String v = requiredText(value, 50, "用户名");
        if (v.length() < 3 || !v.matches("[A-Za-z0-9_.-]{3,50}")) throw validation("用户名须为3至50位字母、数字或 . _ -");
        return v;
    }

    private static String studentNo(String value) {
        String v = requiredText(value, 30, "学号");
        return v;
    }

    private static void passwordLength(char[] password, String label) {
        if (password == null || password.length < 8 || password.length > 128) throw validation(label + "须为8至128位");
    }

    private static String fullName(String value) {
        String v = requiredText(value, 100, "姓名");
        return v;
    }

    private static String operationId(String value) {
        String id = value == null ? "" : value.trim();
        if (id.isEmpty() || id.length() > 64) throw validation("操作编号不能为空且不能超过64位");
        return id;
    }

    private static String description(String value, boolean recharge) {
        String d = value == null ? "" : value.trim();
        if (recharge && d.isEmpty()) return "校园卡充值";
        if (d.isEmpty() || d.length() > 255) throw validation(recharge ? "备注不能超过255位" : "消费说明须为1至255位");
        return d;
    }

    private static BigDecimal money(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.compareTo(new BigDecimal("9999999999.99")) > 0)
            throw validation("金额必须大于0、最多两位小数且不能超过9999999999.99");
        return amount.setScale(2);
    }

    private static TransactionFilter normalizedFilter(TransactionFilter f, Long userId) {
        if (f == null) throw validation("查询条件不能为空");
        validatePage(f.page(), f.pageSize());
        validateDates(f.from(), f.to());
        return new TransactionFilter(userId, f.type(), f.from(), f.to(), f.page(), f.pageSize());
    }

    private static String newCardNumber() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static final class Auth implements AuthService {
        private final Services s;
        Auth(Services s) { this.s = s; }

        @Override public SessionContext login(String username, char[] password) {
            String name = username == null ? "" : username.trim();
            if (password == null) throw error("LOGIN_FAILED", "用户名或密码错误");
            return s.uow.read(c -> {
                UserAccount account = s.users.findByUsername(c, name).orElse(null);
                if (account == null || !PasswordHasher.verify(password, account.passwordHash()))
                    throw error("LOGIN_FAILED", "用户名或密码错误");
                User user = account.user();
                if (!user.enabled()) throw error("USER_DISABLED", "账户已停用");
                SessionContext context = new SessionContext(UUID.randomUUID().toString(), user.id(), user.role(), user.fullName());
                s.sessions.put(context.token(), context);
                return context;
            });
        }

        @Override public void logout(SessionContext session) {
            if (session == null || session.token() == null) return;
            SessionContext registered = s.sessions.get(session.token());
            if (registered != null && registered.equals(session)) s.sessions.remove(session.token(), registered);
        }

        @Override public User currentUser(SessionContext session) {
            return s.uow.read(c -> authorize(s, c, session).user);
        }

        @Override public void changePassword(SessionContext session, char[] oldPassword, char[] newPassword) {
            passwordLength(newPassword, "新密码");
            long userId = s.uow.write(c -> {
                Principal p = authorize(s, c, session);
                UserAccount locked = s.users.findById(c, p.user.id(), true)
                        .orElseThrow(() -> error("AUTH_REQUIRED", "登录用户不存在，请重新登录"));
                if (!locked.user().enabled()) throw error("USER_DISABLED", "账户已停用");
                if (locked.user().role() != session.role()) throw error("FORBIDDEN", "账户权限已变更，请重新登录");
                if (!PasswordHasher.verify(oldPassword, locked.passwordHash())) throw error("LOGIN_FAILED", "原密码错误");
                s.users.updatePassword(c, p.user.id(), PasswordHasher.hash(newPassword));
                return p.user.id();
            });
            s.sessions.entrySet().removeIf(e -> e.getValue().userId() == userId
                    && !e.getKey().equals(session.token()));
        }
    }

    private static final class Cards implements CardService {
        private final Services s;
        Cards(Services s) { this.s = s; }

        @Override public CampusCard getCard(SessionContext session) {
            return s.uow.read(c -> {
                Principal p = authorize(s, c, session);
                return s.cards.findByUserId(c, p.user.id(), false)
                        .orElseThrow(() -> error("NOT_FOUND", "未找到校园卡"));
            });
        }

        @Override public TransactionRecord recharge(SessionContext session, BigDecimal amount, String description, String opId) {
            return write(session, TransactionType.RECHARGE, amount, description, opId);
        }
        @Override public TransactionRecord consume(SessionContext session, BigDecimal amount, String description, String opId) {
            return write(session, TransactionType.CONSUMPTION, amount, description, opId);
        }

        private TransactionRecord write(SessionContext session, TransactionType type, BigDecimal amount, String desc, String rawOp) {
            BigDecimal value = money(amount);
            String detail = description(desc, type == TransactionType.RECHARGE);
            String op = operationId(rawOp);
            return s.uow.write(c -> {
                Principal p = authorize(s, c, session);
                UserAccount lockedUser = s.users.findById(c, p.user.id(), true)
                        .orElseThrow(() -> error("AUTH_REQUIRED", "登录用户不存在，请重新登录"));
                if (!lockedUser.user().enabled()) throw error("USER_DISABLED", "账户已停用");
                if (lockedUser.user().role() != session.role()) throw error("FORBIDDEN", "账户权限已变更，请重新登录");
                CampusCard card = s.cards.findByUserId(c, p.user.id(), true)
                        .orElseThrow(() -> error("NOT_FOUND", "未找到校园卡"));
                TransactionRecord previous = s.transactions.findByOperationId(c, op).orElse(null);
                if (previous != null) {
                    if (previous.userId() == p.user.id() && previous.type() == type
                            && previous.amount().compareTo(value) == 0 && Objects.equals(previous.description(), detail)) return previous;
                    throw error("OPERATION_CONFLICT", "该操作编号已用于其他操作");
                }
                BigDecimal before = card.balance();
                BigDecimal after;
                if (type == TransactionType.RECHARGE) {
                    after = before.add(value);
                    if (after.compareTo(new BigDecimal("9999999999.99")) > 0) throw validation("充值后余额超出允许范围");
                } else {
                    if (before.compareTo(value) < 0) throw error("INSUFFICIENT_BALANCE", "校园卡余额不足");
                    after = before.subtract(value);
                }
                s.cards.updateBalance(c, card.id(), after);
                return s.transactions.insert(c, card.id(), type, value, before, after, detail, p.user.id(), op);
            });
        }

        @Override public Optional<TransactionRecord> findOperation(SessionContext session, String rawOp) {
            String op = operationId(rawOp);
            return s.uow.read(c -> {
                Principal p = authorize(s, c, session);
                Optional<TransactionRecord> found = s.transactions.findByOperationId(c, op);
                if (found.isPresent() && found.get().userId() != p.user.id()) return Optional.empty();
                return found;
            });
        }
    }

    private static final class Records implements RecordService {
        private final Services s;
        Records(Services s) { this.s = s; }

        @Override public Page<TransactionRecord> query(SessionContext session, TransactionFilter filter) {
            if (filter == null) throw validation("查询条件不能为空");
            return s.uow.read(c -> {
                Principal p = authorize(s, c, session);
                if (!p.admin() && filter != null && filter.userId() != null && filter.userId() != p.user.id())
                    throw error("FORBIDDEN", "不能查询其他用户的流水");
                return s.transactions.query(c, normalizedFilter(filter, p.admin() ? filter.userId() : p.user.id()));
            });
        }

        @Override public String exportCsv(SessionContext session, TransactionFilter filter) {
            if (filter == null) throw validation("查询条件不能为空");
            return s.uow.read(c -> {
                Principal p = authorize(s, c, session);
                if (!p.admin() && filter != null && filter.userId() != null && filter.userId() != p.user.id())
                    throw error("FORBIDDEN", "不能导出其他用户的流水");
                TransactionFilter base = normalizedFilter(filter, p.admin() ? filter.userId() : p.user.id());
                StringBuilder out = new StringBuilder("\uFEFF");
                csvRow(out, "流水编号", "用户编号", "用户名", "姓名", "类型", "金额", "变更前余额", "变更后余额", "说明", "操作人编号", "操作编号", "时间");
                int page = 1;
                long total;
                do {
                    Page<TransactionRecord> rows = s.transactions.query(c,
                            new TransactionFilter(base.userId(), base.type(), base.from(), base.to(), page, 100));
                    total = rows.total();
                    for (TransactionRecord r : rows.items()) {
                        csvRow(out, Long.toString(r.id()), Long.toString(r.userId()), safeText(r.username()), safeText(r.fullName()),
                                r.type().name(), r.amount().toPlainString(), r.balanceBefore().toPlainString(), r.balanceAfter().toPlainString(),
                                safeText(r.description()), Long.toString(r.actorId()), safeText(r.operationId()), r.createdAt().toString());
                    }
                    page++;
                } while ((long) (page - 1) * 100 < total);
                return out.toString();
            });
        }

        private static String safeText(String value) {
            if (value == null || value.isEmpty()) return "";
            if (value.charAt(0) == '\t' || value.charAt(0) == '\r' || value.charAt(0) == '\n') return "'" + value;
            int i = 0;
            while (i < value.length() && Character.isWhitespace(value.charAt(i))) i++;
            if (i < value.length() && "=+-@".indexOf(value.charAt(i)) >= 0) return "'" + value;
            return value;
        }
        private static void csvRow(StringBuilder out, String... fields) {
            for (int i = 0; i < fields.length; i++) {
                if (i > 0) out.append(',');
                out.append('"').append(fields[i].replace("\"", "\"\"")).append('"');
            }
            out.append("\r\n");
        }
    }

    private static final class Admin implements AdminService {
        private final Services s;
        Admin(Services s) { this.s = s; }

        @Override public Page<User> queryUsers(SessionContext session, UserFilter filter) {
            if (filter == null) throw validation("查询条件不能为空");
            validatePage(filter.page(), filter.pageSize());
            return s.uow.read(c -> {
                requireAdmin(s, c, session);
                String keyword = filter.keyword() == null ? null : filter.keyword().trim();
                return s.users.query(c, new UserFilter(keyword, filter.enabled(), filter.page(), filter.pageSize()));
            });
        }

        @Override public User createUser(SessionContext session, CreateUserRequest request) {
            if (request == null) throw validation("开户信息不能为空");
            String username = username(request.username());
            String password = request.password();
            if (password == null || password.length() < 8 || password.length() > 128) throw validation("密码须为8至128位");
            String name = fullName(request.fullName());
            String studentNo = studentNo(request.studentNo());
            char[] passwordChars = password.toCharArray();
            String hash;
            try { hash = PasswordHasher.hash(passwordChars); }
            finally { Arrays.fill(passwordChars, '\0'); }
            String cardNo = newCardNumber();
            return s.uow.write(c -> {
                requireAdminForWrite(s, c, session);
                User created = s.users.insert(c, username, hash, name, studentNo);
                s.cards.insert(c, created.id(), cardNo);
                return created;
            });
        }

        @Override public User updateUser(SessionContext session, UpdateUserRequest request) {
            if (request == null || request.userId() <= 0) throw validation("用户编号无效");
            String name = fullName(request.fullName());
            String studentNo = studentNo(request.studentNo());
            UpdateUserRequest normalized = new UpdateUserRequest(request.userId(), name, studentNo);
            return s.uow.write(c -> {
                requireAdminForWrite(s, c, session);
                UserAccount target = s.users.findById(c, request.userId(), true)
                        .orElseThrow(() -> error("NOT_FOUND", "用户不存在"));
                if (target.user().role() == Role.ADMIN) throw error("FORBIDDEN", "不能通过用户管理修改管理员");
                return s.users.update(c, normalized);
            });
        }

        @Override public void setEnabled(SessionContext session, long userId, boolean enabled) {
            if (userId <= 0) throw validation("用户编号无效");
            s.uow.write(c -> {
                requireAdminForWrite(s, c, session);
                UserAccount target = s.users.findById(c, userId, true)
                        .orElseThrow(() -> error("NOT_FOUND", "用户不存在"));
                if (target.user().role() == Role.ADMIN) throw error("FORBIDDEN", "不能通过用户管理停用或启用管理员");
                s.users.setEnabled(c, userId, enabled);
                return null;
            });
        }
    }

    private static final class Stats implements StatisticsService {
        private final Services s;
        Stats(Services s) { this.s = s; }
        @Override public Statistics summarize(SessionContext session, LocalDate from, LocalDate to) {
            validateDates(from, to);
            return s.uow.read(c -> {
                requireAdmin(s, c, session);
                return s.statistics.summarize(c, from, to);
            });
        }
    }
}
