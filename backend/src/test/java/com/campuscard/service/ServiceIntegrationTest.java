package com.campuscard.service;

import com.campuscard.api.ApplicationServices;
import com.campuscard.config.JdbcConnectionProvider;
import com.campuscard.config.JdbcUnitOfWork;
import com.campuscard.dao.CardDao;
import com.campuscard.dao.TransactionDao;
import com.campuscard.dao.jdbc.*;
import com.campuscard.dto.*;
import com.campuscard.exception.BusinessException;
import com.campuscard.model.*;
import com.campuscard.security.PasswordHasher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ResourceLock("campus-card-test-database")
class ServiceIntegrationTest {
    private JdbcConnectionProvider provider;
    private ApplicationServices services;
    private SessionContext admin;
    private SessionContext student;
    private SessionContext otherStudent;
    private long studentId;
    private long otherStudentId;

    @BeforeAll void connectAndApplySchema() throws Exception {
        String url = System.getenv("CC_TEST_DB_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "CC_TEST_DB_URL is not configured; DB integration tests skipped");
        String database = databaseName(url);
        assertTrue(database.equals("campus_card_test") || database.endsWith("_test"),
                "Refusing to use a database not explicitly named for testing: " + database);
        provider = new JdbcConnectionProvider(new com.campuscard.config.DatabaseConfig(
                url, System.getenv().getOrDefault("CC_TEST_DB_USER", ""), System.getenv().getOrDefault("CC_TEST_DB_PASSWORD", "")));
        try (Connection c = provider.open()) {
            String actual;
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT DATABASE()")) {
                assertTrue(rs.next()); actual = rs.getString(1);
            }
            assertEquals(database, actual, "JDBC server selected an unexpected database");
            applySqlFile(c, "schema.sql");
            applySqlFile(c, "seed.sql");
        }
    }

    @BeforeEach void resetFixture() throws Exception {
        try (Connection c = provider.open(); Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM transaction_records");
            s.executeUpdate("DELETE FROM campus_cards");
            s.executeUpdate("DELETE FROM users");
            s.executeUpdate("ALTER TABLE transaction_records AUTO_INCREMENT=1");
            s.executeUpdate("ALTER TABLE campus_cards AUTO_INCREMENT=1");
            s.executeUpdate("ALTER TABLE users AUTO_INCREMENT=1");
        }
        insertUser("admin", "admin-pass-123", "System Admin", null, "ADMIN", true);
        insertUser("student", "student-pass-123", "Student One", "S-100", "USER", true);
        insertUser("other", "other-pass-123", "Student Two", "S-200", "USER", true);
        services = createServices(new JdbcTransactionDao());
        admin = services.auth().login("admin", "admin-pass-123".toCharArray());
        student = services.auth().login("student", "student-pass-123".toCharArray());
        otherStudent = services.auth().login("other", "other-pass-123".toCharArray());
        studentId = student.userId(); otherStudentId = otherStudent.userId();
    }

    private ApplicationServices createServices(TransactionDao transactionDao) {
        return createServices(new JdbcCardDao(), transactionDao);
    }

    private ApplicationServices createServices(CardDao cardDao, TransactionDao transactionDao) {
        return ServiceFactory.create(new JdbcUnitOfWork(provider), new JdbcUserDao(), cardDao, transactionDao, new JdbcStatisticsDao());
    }

    private static void applySqlFile(Connection c, String fileName) throws IOException, SQLException {
        Path sqlFile = Path.of("database", fileName);
        if (!Files.isRegularFile(sqlFile)) sqlFile = Path.of("..", "database", fileName);
        String sql = Files.readString(sqlFile, StandardCharsets.UTF_8);
        for (String statement : sql.split(";")) if (!statement.isBlank()) {
            try (Statement s = c.createStatement()) { s.execute(statement); }
        }
    }

    private static String databaseName(String jdbcUrl) {
        int query = jdbcUrl.indexOf('?');
        String base = query < 0 ? jdbcUrl : jdbcUrl.substring(0, query);
        int slash = base.lastIndexOf('/');
        if (!base.startsWith("jdbc:mysql:") || slash < "jdbc:mysql://".length() || slash == base.length() - 1)
            throw new IllegalArgumentException("CC_TEST_DB_URL must select a MySQL database");
        return base.substring(slash + 1);
    }

    private void insertUser(String username, String password, String name, String studentNo, String role, boolean enabled) throws SQLException {
        try (Connection c = provider.open(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO users(username,password_hash,full_name,student_no,role,enabled) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username); ps.setString(2, PasswordHasher.hash(password.toCharArray())); ps.setString(3, name);
            ps.setString(4, studentNo); ps.setString(5, role); ps.setBoolean(6, enabled); ps.executeUpdate();
            long id;
            try (ResultSet keys = ps.getGeneratedKeys()) { assertTrue(keys.next()); id = keys.getLong(1); }
            if ("USER".equals(role)) try (PreparedStatement card = c.prepareStatement("INSERT INTO campus_cards(user_id,card_no,balance) VALUES(?,?,100.00)")) {
                card.setLong(1, id); card.setString(2, "CARD-" + username); card.executeUpdate();
            }
        }
    }

    @Test void loginLogoutForgeryAndCurrentUserDoNotExposeHash() {
        assertThrowsCode("LOGIN_FAILED", () -> services.auth().login("student", "wrong-pass".toCharArray()));
        assertThrowsCode("LOGIN_FAILED", () -> services.auth().login("missing", "student-pass-123".toCharArray()));
        assertEquals("Student One", services.auth().currentUser(student).fullName());
        SessionContext forged = new SessionContext(student.token(), otherStudentId, Role.ADMIN, "forged");
        assertThrowsCode("FORBIDDEN", () -> services.auth().currentUser(forged));
        assertDoesNotThrow(() -> services.auth().logout(forged));
        assertDoesNotThrow(() -> services.auth().currentUser(student), "forged logout must not revoke the registered session");
        services.auth().logout(student);
        assertThrowsCode("AUTH_REQUIRED", () -> services.auth().currentUser(student));
    }

    @Test void adminCreatesAtomicallyAndEnforcesUniqueUsernameAndStudentNumber() {
        User created = services.admin().createUser(admin, new CreateUserRequest("new.user", "long-password", "New User", "NEW-100"));
        assertEquals(Role.USER, created.role());
        SessionContext createdSession = services.auth().login("new.user", "long-password".toCharArray());
        assertEquals(new BigDecimal("0.00"), services.cards().getCard(createdSession).balance());
        assertThrowsCode("DUPLICATE", () -> services.admin().createUser(admin,
                new CreateUserRequest("another", "long-password", "Duplicate No", "NEW-100")));
        assertThrowsCode("DUPLICATE", () -> services.admin().createUser(admin,
                new CreateUserRequest("new.user", "long-password", "Duplicate User", "NEW-101")));
        try (Connection c = provider.open(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM users WHERE username='another'")) {
            rs.next(); assertEquals(0, rs.getInt(1), "failed user creation should leave no partial account");
        } catch (SQLException e) { fail(e); }
        assertThrowsCode("FORBIDDEN", () -> services.admin().createUser(student,
                new CreateUserRequest("bad.create", "long-password", "Bad", "BAD-001")));
    }

    @Test void failedCardInsertRollsBackTheNewUser() throws SQLException {
        int cardsBefore = countRows("campus_cards");
        CardDao failing = new DelegatingCardDao(new JdbcCardDao()) {
            @Override public CampusCard insert(Connection c, long userId, String cardNo) throws SQLException {
                throw new SQLException("forced card insert failure");
            }
        };
        ApplicationServices broken = createServices(failing, new JdbcTransactionDao());
        SessionContext adminSession = broken.auth().login("admin", "admin-pass-123".toCharArray());
        assertThrowsCode("DATABASE_ERROR", () -> broken.admin().createUser(adminSession,
                new CreateUserRequest("rollback.user", "long-password", "Rollback User", "ROLLBACK-1")));
        assertEquals(0, countRows("users", "username", "rollback.user"), "the user row must rollback with the failed card insert");
        assertEquals(cardsBefore, countRows("campus_cards"));
    }

    private int countRows(String table) throws SQLException {
        try (Connection c = provider.open(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next(); return rs.getInt(1);
        }
    }

    private int countRows(String table, String column, String value) throws SQLException {
        try (Connection c = provider.open(); PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE " + column + "=?")) {
            ps.setString(1, value); try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    @Test void rechargeAndConsumptionMaintainExpectedBalanceAndValidateMoney() {
        services.cards().recharge(student, new BigDecimal("50.00"), "top up", "money-1");
        services.cards().consume(student, new BigDecimal("30.00"), "lunch", "money-2");
        assertEquals(new BigDecimal("120.00"), services.cards().getCard(student).balance());
        assertThrowsCode("VALIDATION", () -> services.cards().recharge(student, BigDecimal.ZERO, "", "invalid-zero"));
        assertThrowsCode("VALIDATION", () -> services.cards().recharge(student, new BigDecimal("-1.00"), "", "invalid-negative-recharge"));
        assertThrowsCode("VALIDATION", () -> services.cards().consume(student, new BigDecimal("-1.00"), "invalid", "invalid-negative-consume"));
        assertThrowsCode("VALIDATION", () -> services.cards().recharge(student, new BigDecimal("1.001"), "", "invalid-scale"));
        assertThrowsCode("VALIDATION", () -> services.cards().recharge(student, new BigDecimal("10000000000.00"), "", "invalid-max"));
        assertThrowsCode("VALIDATION", () -> services.cards().recharge(student, new BigDecimal("9999999999.99"), "", "overflow"));
        assertThrowsCode("INSUFFICIENT_BALANCE", () -> services.cards().consume(student, new BigDecimal("120.01"), "too much", "insufficient"));
        assertEquals(new BigDecimal("120.00"), services.cards().getCard(student).balance());
    }

    @Test void failedTransactionInsertRollsBackBalance() {
        TransactionDao failing = new DelegatingTransactionDao(new JdbcTransactionDao()) {
            @Override public TransactionRecord insert(Connection c, long cardId, TransactionType type, BigDecimal amount,
                    BigDecimal before, BigDecimal after, String description, long actorId, String operationId) throws SQLException {
                throw new SQLException("forced transaction insert failure");
            }
        };
        ApplicationServices broken = createServices(failing);
        SessionContext fresh = broken.auth().login("student", "student-pass-123".toCharArray());
        assertThrowsCode("DATABASE_ERROR", () -> broken.cards().consume(fresh, new BigDecimal("20.00"), "lunch", "rollback-op"));
        assertEquals(new BigDecimal("100.00"), broken.cards().getCard(fresh).balance());
        assertTrue(broken.records().query(fresh, new TransactionFilter(null, null, null, null, 1, 20)).items().isEmpty());
    }

    @Test void simultaneousSpendingCannotOverdraw() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                final int n = i;
                results.add(pool.submit(() -> {
                    SessionContext session = services.auth().login("student", "student-pass-123".toCharArray());
                    start.await();
                    try { services.cards().consume(session, new BigDecimal("80.00"), "parallel", "parallel-" + n); return "ok"; }
                    catch (BusinessException e) { return e.code(); }
                }));
            }
            start.countDown();
            List<String> codes = List.of(results.get(0).get(20, TimeUnit.SECONDS), results.get(1).get(20, TimeUnit.SECONDS));
            assertEquals(1, codes.stream().filter("ok"::equals).count());
            assertEquals(1, codes.stream().filter("INSUFFICIENT_BALANCE"::equals).count());
            assertEquals(new BigDecimal("20.00"), services.cards().getCard(student).balance());
        } finally { pool.shutdownNow(); }
    }

    @Test void operationIdIsIdempotentAndConflictsCannotChangeBalance() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Callable<TransactionRecord> attempt = () -> { start.await(); return services.cards().consume(student, new BigDecimal("10.00"), "same", "same-op"); };
            Future<TransactionRecord> a = pool.submit(attempt), b = pool.submit(attempt); start.countDown();
            assertEquals(a.get(20, TimeUnit.SECONDS).id(), b.get(20, TimeUnit.SECONDS).id());
            assertEquals(new BigDecimal("90.00"), services.cards().getCard(student).balance());
            assertEquals(1, services.records().query(student, new TransactionFilter(null, null, null, null, 1, 20)).total());
            assertThrowsCode("OPERATION_CONFLICT", () -> services.cards().consume(student, new BigDecimal("11.00"), "same", "same-op"));
            assertThrowsCode("OPERATION_CONFLICT", () -> services.cards().recharge(student, new BigDecimal("10.00"), "same", "same-op"));
            assertEquals(new BigDecimal("90.00"), services.cards().getCard(student).balance());
            assertThrowsCode("OPERATION_CONFLICT", () -> services.cards().consume(otherStudent,
                    new BigDecimal("1.00"), "foreign collision", "same-op"));
            assertTrue(services.cards().findOperation(student, "same-op").isPresent());
            assertTrue(services.cards().findOperation(otherStudent, "same-op").isEmpty());
            assertEquals(0, services.records().query(admin, new TransactionFilter(otherStudentId, null, null, null, 1, 20)).total());
        } finally { pool.shutdownNow(); }
    }

    @Test void accessControlDisabledAccountsAndPasswordSessionInvalidation() {
        assertThrowsCode("FORBIDDEN", () -> services.records().query(student, new TransactionFilter(otherStudentId, null, null, null, 1, 20)));
        assertThrowsCode("FORBIDDEN", () -> services.admin().queryUsers(student, new UserFilter(null, null, 1, 20)));
        assertThrowsCode("FORBIDDEN", () -> services.statistics().summarize(student, null, null));
        SessionContext second = services.auth().login("student", "student-pass-123".toCharArray());
        services.auth().changePassword(student, "student-pass-123".toCharArray(), "new-password-123".toCharArray());
        assertDoesNotThrow(() -> services.auth().currentUser(student));
        assertThrowsCode("AUTH_REQUIRED", () -> services.auth().currentUser(second));
        assertThrowsCode("LOGIN_FAILED", () -> services.auth().login("student", "student-pass-123".toCharArray()));
        SessionContext newLogin = services.auth().login("student", "new-password-123".toCharArray());
        services.admin().setEnabled(admin, studentId, false);
        assertThrowsCode("USER_DISABLED", () -> services.auth().login("student", "new-password-123".toCharArray()));
        assertThrowsCode("USER_DISABLED", () -> services.cards().recharge(newLogin, new BigDecimal("1.00"), "", "disabled-op"));
    }

    @Test void adminCanEditUsersFilterByEnabledStateButCannotEditOrDisableAdmin() {
        User updated = services.admin().updateUser(admin, new UpdateUserRequest(studentId, "Renamed Student", "S-101"));
        assertEquals("Renamed Student", updated.fullName());
        assertEquals("S-101", updated.studentNo());
        services.admin().setEnabled(admin, studentId, false);
        assertEquals(1, services.admin().queryUsers(admin, new UserFilter(null, false, 1, 20)).total());
        assertEquals(1, services.admin().queryUsers(admin, new UserFilter(null, true, 1, 20)).total());
        assertEquals(2, services.admin().queryUsers(admin, new UserFilter(null, null, 1, 20)).total());
        assertThrowsCode("FORBIDDEN", () -> services.admin().updateUser(admin,
                new UpdateUserRequest(admin.userId(), "Changed Admin", "ADMIN-1")));
        assertThrowsCode("FORBIDDEN", () -> services.admin().setEnabled(admin, admin.userId(), false));
        assertEquals(1, services.admin().queryUsers(admin, new UserFilter("Renamed Student", false, 1, 20)).total());
        assertDoesNotThrow(() -> services.auth().currentUser(admin));
    }

    @Test void queriesArePagedInclusiveByEndDateAndCsvEscapesFormulaText() throws Exception {
        services.cards().consume(student, new BigDecimal("1.00"), "=SUM(1,2) says \"hi\"", "csv-op-1");
        services.cards().consume(student, new BigDecimal("1.00"), "ordinary", "csv-op-2");
        try (Connection c = provider.open(); PreparedStatement ps = c.prepareStatement("UPDATE transaction_records SET created_at=? WHERE operation_id=?")) {
            ps.setTimestamp(1, Timestamp.valueOf("2025-04-05 23:59:59.999999")); ps.setString(2, "csv-op-1"); ps.executeUpdate();
            ps.setTimestamp(1, Timestamp.valueOf("2025-04-06 00:00:00")); ps.setString(2, "csv-op-2"); ps.executeUpdate();
        }
        TransactionFilter day = new TransactionFilter(null, null, LocalDate.parse("2025-04-05"), LocalDate.parse("2025-04-05"), 1, 1);
        Page<TransactionRecord> page = services.records().query(student, day);
        assertEquals(1, page.total()); assertEquals(1, page.items().size());
        assertEquals("csv-op-1", page.items().get(0).operationId());
        TransactionFilter range = new TransactionFilter(studentId, null, LocalDate.parse("2025-04-05"), LocalDate.parse("2025-04-06"), 1, 1);
        Page<TransactionRecord> firstRangePage = services.records().query(admin, range);
        assertEquals(2, firstRangePage.total());
        assertEquals("csv-op-2", firstRangePage.items().get(0).operationId(), "to date includes transactions through its full day");
        Page<TransactionRecord> secondPage = services.records().query(admin, new TransactionFilter(studentId, null,
                LocalDate.parse("2025-04-05"), LocalDate.parse("2025-04-06"), 2, 1));
        assertEquals("csv-op-1", secondPage.items().get(0).operationId());
        assertThrowsCode("VALIDATION", () -> services.records().query(student,
                new TransactionFilter(null, null, LocalDate.parse("2025-04-07"), LocalDate.parse("2025-04-06"), 1, 20)));
        String csv = services.records().exportCsv(student, new TransactionFilter(null, null, null, null, 1, 20));
        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("'=SUM(1,2) says \"\"hi\"\""));
        assertTrue(csv.contains("\"csv-op-1\""));
        assertThrowsCode("VALIDATION", () -> services.records().query(student, null));
        assertThrowsCode("VALIDATION", () -> services.records().query(admin, null));
        assertThrowsCode("VALIDATION", () -> services.records().exportCsv(admin, null));
    }

    @Test void statisticsExcludeAdminsAndDateRangeOnlyChangesTransactionTotals() {
        services.cards().recharge(student, new BigDecimal("5.00"), "", "stats-topup");
        services.cards().consume(student, new BigDecimal("2.00"), "meal", "stats-meal");
        services.admin().setEnabled(admin, otherStudentId, false);
        Statistics all = services.statistics().summarize(admin, null, null);
        assertEquals(2, all.users()); assertEquals(1, all.enabledUsers());
        assertEquals(new BigDecimal("203.00"), all.totalBalance());
        Statistics beforeData = services.statistics().summarize(admin, LocalDate.parse("2000-01-01"), LocalDate.parse("2000-01-01"));
        assertEquals(all.users(), beforeData.users());
        assertEquals(all.totalBalance(), beforeData.totalBalance());
        assertEquals(0, beforeData.transactionCount());
        assertEquals(2, all.transactionCount());
    }

    @Test void missingDatabaseProviderIsTranslatedToBusinessException() {
        JdbcUnitOfWork unavailable = new JdbcUnitOfWork(() -> { throw new SQLException("offline"); });
        BusinessException error = assertThrows(BusinessException.class, () -> unavailable.read(c -> null));
        assertEquals("DATABASE_ERROR", error.code());
        assertNotNull(error.getCause());
    }

    private static void assertThrowsCode(String code, org.junit.jupiter.api.function.Executable action) {
        BusinessException error = assertThrows(BusinessException.class, action);
        assertEquals(code, error.code(), error.getMessage());
    }

    private abstract static class DelegatingTransactionDao implements TransactionDao {
        private final TransactionDao delegate;
        DelegatingTransactionDao(TransactionDao delegate) { this.delegate = delegate; }
        @Override public Optional<TransactionRecord> findByOperationId(Connection c, String id) throws SQLException { return delegate.findByOperationId(c, id); }
        @Override public Page<TransactionRecord> query(Connection c, TransactionFilter f) throws SQLException { return delegate.query(c, f); }
    }

    private abstract static class DelegatingCardDao implements CardDao {
        private final CardDao delegate;
        DelegatingCardDao(CardDao delegate) { this.delegate = delegate; }
        @Override public Optional<CampusCard> findByUserId(Connection c, long userId, boolean lock) throws SQLException {
            return delegate.findByUserId(c, userId, lock);
        }
        @Override public void updateBalance(Connection c, long cardId, BigDecimal balance) throws SQLException {
            delegate.updateBalance(c, cardId, balance);
        }
    }
}
