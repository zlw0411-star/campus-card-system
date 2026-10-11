package com.campuscard.server;

import com.campuscard.security.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP/session integration tests backed by the isolated MySQL test database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = CampusCardServer.class)
@ContextConfiguration(initializers = CampusCardHttpIntegrationTest.TestDatabaseInitializer.class)
@EnabledIfEnvironmentVariable(named = "CC_TEST_DB_URL", matches = ".+")
class CampusCardHttpIntegrationTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ADMIN_PASSWORD = "test-admin-pass";
    private static final String STUDENT_PASSWORD = "test-student-pass";
    private static final String OTHER_PASSWORD = "test-other-pass";
    private static final String ADMIN_HASH = PasswordHasher.hash(ADMIN_PASSWORD.toCharArray());
    private static final String STUDENT_HASH = PasswordHasher.hash(STUDENT_PASSWORD.toCharArray());
    private static final String OTHER_HASH = PasswordHasher.hash(OTHER_PASSWORD.toCharArray());
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach void resetTestFixture() {
        jdbc.update("DELETE FROM transaction_records");
        jdbc.update("DELETE FROM campus_cards");
        jdbc.update("DELETE FROM users");
        jdbc.update("ALTER TABLE transaction_records AUTO_INCREMENT=1");
        jdbc.update("ALTER TABLE campus_cards AUTO_INCREMENT=1");
        jdbc.update("ALTER TABLE users AUTO_INCREMENT=1");
        jdbc.update("INSERT INTO users(username,password_hash,full_name,student_no,role,enabled) VALUES(?,?,?,?,?,TRUE)",
                "root-admin", ADMIN_HASH, "Test Administrator", null, "ADMIN");
        jdbc.update("INSERT INTO users(username,password_hash,full_name,student_no,role,enabled) VALUES(?,?,?,?,?,TRUE)",
                "test-student", STUDENT_HASH, "Test Student", "HTTP-100", "USER");
        jdbc.update("INSERT INTO users(username,password_hash,full_name,student_no,role,enabled) VALUES(?,?,?,?,?,TRUE)",
                "other-student", OTHER_HASH, "Other Student", "HTTP-200", "USER");
        long studentId = userId("test-student");
        long otherId = userId("other-student");
        long adminId = userId("root-admin");
        jdbc.update("INSERT INTO campus_cards(user_id,card_no,balance) VALUES(?,?,?)", studentId, "CARD-HTTP-100", "100.00");
        jdbc.update("INSERT INTO campus_cards(user_id,card_no,balance) VALUES(?,?,?)", otherId, "CARD-HTTP-200", "200.00");
        LocalDateTime now = LocalDateTime.now(SHANGHAI).withNano(0);
        insertTransaction(studentId, "RECHARGE", "100.00", "0.00", "100.00", "test initial balance", adminId, "fixture-student-init", now.minusMinutes(2));
        insertTransaction(otherId, "RECHARGE", "200.00", "0.00", "200.00", "other initial balance", adminId, "fixture-other-init", now.minusMinutes(1));
    }

    @Test void csrfCookieLoginSessionRotationAndStringMoneyAreEnforced() throws Exception {
        Client client = client();
        HttpResponse<String> anonymous = client.get("/api/auth/me");
        assertEquals(401, anonymous.statusCode());
        assertEquals("AUTH_REQUIRED", json(anonymous).path("code").asText());

        Csrf before = client.getCsrf();
        String beforeId = client.sessionId();
        HttpResponse<String> login = client.post("/api/auth/login", body("username", "test-student", "password", STUDENT_PASSWORD), before);
        assertEquals(200, login.statusCode(), login.body());
        assertNotEquals(beforeId, client.sessionId(), "login must rotate the HTTP session id");
        assertTrue(login.headers().allValues("set-cookie").stream().anyMatch(CampusCardHttpIntegrationTest::isSecureSessionCookie),
                "session cookie must be HttpOnly");
        JsonNode user = json(login);
        assertEquals("USER", user.path("role").asText());
        assertFalse(user.has("passwordHash"));
        assertFalse(user.has("password_hash"));

        Csrf after = client.getCsrf();
        assertNotEquals(before.token(), after.token(), "CSRF token should be rotated after login");
        String operationId = UUID.randomUUID().toString();
        String recharge = body("amount", "5.00", "description", "HTTP top-up", "operationId", operationId);
        HttpResponse<String> missingCsrf = client.post("/api/card/recharge", recharge, null);
        assertEquals(403, missingCsrf.statusCode());
        assertEquals("CSRF_INVALID", json(missingCsrf).path("code").asText());
        HttpResponse<String> invalidCsrf = client.post("/api/card/recharge", recharge, new Csrf("invalid", after.headerName()));
        assertEquals(403, invalidCsrf.statusCode());
        assertEquals("CSRF_INVALID", json(invalidCsrf).path("code").asText());

        HttpResponse<String> written = client.post("/api/card/recharge", recharge, after);
        assertEquals(200, written.statusCode(), written.body());
        assertEquals("5.00", json(written).path("amount").asText());
        assertEquals("105.00", json(written).path("balanceAfter").asText());
        assertTrue(json(written).path("amount").isTextual(), "money must be serialized as decimal text");
        HttpResponse<String> card = client.get("/api/card");
        assertEquals("105.00", json(card).path("balance").asText());
    }

    @Test void roleOwnershipIdempotenceAndDisabledCoreSessionsAreEnforced() throws Exception {
        Client student = loggedIn("test-student", STUDENT_PASSWORD);
        long otherId = userId("other-student");
        HttpResponse<String> adminOnly = student.get("/api/admin/users");
        assertEquals(403, adminOnly.statusCode());
        assertEquals("FORBIDDEN", json(adminOnly).path("code").asText());
        HttpResponse<String> crossUser = student.get("/api/transactions?userId=" + otherId);
        assertEquals(403, crossUser.statusCode());

        String operationId = UUID.randomUUID().toString();
        String operation = body("amount", "2.50", "description", "one write", "operationId", operationId);
        HttpResponse<String> first = student.postWithFreshCsrf("/api/card/consume", operation);
        assertEquals(200, first.statusCode(), first.body());
        HttpResponse<String> repeated = student.postWithFreshCsrf("/api/card/consume", operation);
        assertEquals(200, repeated.statusCode(), repeated.body());
        assertEquals(json(first).path("id").asLong(), json(repeated).path("id").asLong());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM transaction_records WHERE operation_id=?", Integer.class, operationId));
        HttpResponse<String> conflict = student.postWithFreshCsrf("/api/card/consume",
                body("amount", "3.00", "description", "different operation", "operationId", operationId));
        assertEquals(409, conflict.statusCode());
        assertEquals("OPERATION_CONFLICT", json(conflict).path("code").asText());

        Client other = loggedIn("other-student", OTHER_PASSWORD);
        HttpResponse<String> hiddenOperation = other.get("/api/card/operations/" + operationId);
        assertEquals(200, hiddenOperation.statusCode());
        assertTrue(hiddenOperation.body().trim().equals("null"), "operation lookup must not reveal another user's record");

        Client admin = loggedIn("root-admin", ADMIN_PASSWORD);
        String disable = body("enabled", false);
        HttpResponse<String> disabled = admin.postWithFreshCsrf("/api/admin/users/" + student.userId + "/enabled", disable, "PATCH");
        assertEquals(204, disabled.statusCode(), disabled.body());
        HttpResponse<String> staleAccess = student.get("/api/card");
        assertEquals(403, staleAccess.statusCode());
        assertEquals("USER_DISABLED", json(staleAccess).path("code").asText());
        Client fresh = client();
        HttpResponse<String> disabledLogin = fresh.login("test-student", STUDENT_PASSWORD);
        assertEquals(403, disabledLogin.statusCode());
        assertEquals("USER_DISABLED", json(disabledLogin).path("code").asText());
    }

    @Test void adminCanReadAllUsersButUserDataNeverLeaksAcrossQueries() throws Exception {
        Client admin = loggedIn("root-admin", ADMIN_PASSWORD);
        HttpResponse<String> users = admin.get("/api/admin/users");
        assertEquals(200, users.statusCode());
        assertEquals(2, json(users).path("total").asInt());
        assertFalse(users.body().contains("passwordHash"));
        assertFalse(users.body().contains("password_hash"));
        HttpResponse<String> records = admin.get("/api/transactions");
        assertEquals(200, records.statusCode());
        assertEquals(2, json(records).path("total").asInt());

        Client student = loggedIn("test-student", STUDENT_PASSWORD);
        HttpResponse<String> ownRecords = student.get("/api/transactions");
        assertEquals(200, ownRecords.statusCode());
        assertEquals(1, json(ownRecords).path("total").asInt());
        assertFalse(ownRecords.body().contains("other-student"));
        HttpResponse<String> forbiddenCreate = student.postWithFreshCsrf("/api/admin/users",
                body("username", "forbidden-create", "password", "valid-password", "fullName", "No", "studentNo", "NO-1"));
        assertEquals(403, forbiddenCreate.statusCode());
    }

    @Test void passwordChangeKeepsCurrentSessionAndRevokesOtherSessionsButAllowsLogout() throws Exception {
        Client current = loggedIn("test-student", STUDENT_PASSWORD);
        Client other = loggedIn("test-student", STUDENT_PASSWORD);

        HttpResponse<String> changed = current.postWithFreshCsrf("/api/auth/password",
                body("oldPassword", STUDENT_PASSWORD, "newPassword", "student-new-pass"));
        assertEquals(204, changed.statusCode(), changed.body());
        assertEquals(200, current.get("/api/auth/me").statusCode(), "the session changing the password remains valid");
        HttpResponse<String> revoked = other.get("/api/auth/me");
        assertEquals(401, revoked.statusCode(), revoked.body());
        assertEquals("AUTH_REQUIRED", json(revoked).path("code").asText());

        HttpResponse<String> logout = other.postWithFreshCsrf("/api/auth/logout", null);
        assertEquals(204, logout.statusCode(), "an invalidated session must still be able to logout cleanly");
    }

    @Test void transactionPagingFiltersCsvBomAndMalformedParametersAreEnforced() throws Exception {
        Client student = loggedIn("test-student", STUDENT_PASSWORD);
        LocalDate today = LocalDate.now(SHANGHAI);
        LocalDateTime now = LocalDateTime.now(SHANGHAI).withNano(0);
        insertTransaction(userId("test-student"), "CONSUMPTION", "12.50", "100.00", "87.50",
                "today filtered lunch", userId("root-admin"), "fixture-today-consume", now.minusSeconds(10));
        insertTransaction(userId("test-student"), "RECHARGE", "20.00", "87.50", "107.50",
                "yesterday recharge", userId("root-admin"), "fixture-yesterday-recharge", now.minusDays(1));

        HttpResponse<String> first = student.get("/api/transactions?page=1&pageSize=1");
        assertEquals(200, first.statusCode(), first.body());
        assertEquals(3, json(first).path("total").asInt());
        assertEquals(1, json(first).path("items").size());
        long firstId = json(first).path("items").get(0).path("id").asLong();
        HttpResponse<String> second = student.get("/api/transactions?page=2&pageSize=1");
        assertEquals(200, second.statusCode(), second.body());
        assertEquals(1, json(second).path("items").size());
        assertNotEquals(firstId, json(second).path("items").get(0).path("id").asLong());

        String todayFilter = "?type=CONSUMPTION&from=" + today + "&to=" + today;
        HttpResponse<String> filtered = student.get("/api/transactions" + todayFilter);
        assertEquals(200, filtered.statusCode(), filtered.body());
        assertEquals(1, json(filtered).path("total").asInt(), "end date includes the complete Shanghai calendar day");
        assertEquals("today filtered lunch", json(filtered).path("items").get(0).path("description").asText());

        HttpResponse<String> csv = student.get("/api/transactions/export" + todayFilter);
        assertEquals(200, csv.statusCode(), csv.body());
        assertTrue(csv.headers().firstValue("content-type").orElse("").contains("text/csv"));
        assertTrue(csv.body().startsWith("\uFEFF"), "CSV should include a UTF-8 BOM");
        assertTrue(csv.body().contains("today filtered lunch"));
        assertFalse(csv.body().contains("yesterday recharge"));

        HttpResponse<String> invalidPage = student.get("/api/transactions?page=1&pageSize=0");
        assertEquals(400, invalidPage.statusCode());
        assertEquals("VALIDATION", json(invalidPage).path("code").asText());
        HttpResponse<String> invalidDate = student.get("/api/transactions?from=not-a-date");
        assertEquals(400, invalidDate.statusCode());
        assertEquals("VALIDATION", json(invalidDate).path("code").asText());
    }

    @Test void dashboardFiltersByRoleAndReturnsAnInclusiveZeroFilledTrend() throws Exception {
        Client student = loggedIn("test-student", STUDENT_PASSWORD);
        HttpResponse<String> own = student.get("/api/dashboard?days=4");
        assertEquals(200, own.statusCode(), own.body());
        JsonNode ownJson = json(own);
        assertEquals(1, ownJson.path("transactionCount").asLong());
        assertEquals(4, ownJson.path("trend").size());
        assertEquals(LocalDate.now(SHANGHAI).minusDays(3).toString(), ownJson.path("trend").get(0).path("date").asText());
        assertEquals(LocalDate.now(SHANGHAI).toString(), ownJson.path("trend").get(3).path("date").asText());
        assertEquals("100.00", ownJson.path("rechargeAmount").asText());
        for (JsonNode point : ownJson.path("trend")) {
            assertTrue(point.path("rechargeAmount").isTextual());
            assertTrue(point.path("consumptionAmount").isTextual());
        }
        HttpResponse<String> invalidDays = student.get("/api/dashboard?days=0");
        assertEquals(400, invalidDays.statusCode());
        assertEquals("VALIDATION", json(invalidDays).path("code").asText());

        Client admin = loggedIn("root-admin", ADMIN_PASSWORD);
        HttpResponse<String> all = admin.get("/api/dashboard?days=4");
        assertEquals(200, all.statusCode(), all.body());
        assertEquals(2, json(all).path("transactionCount").asLong());
        HttpResponse<String> health = client().get("/api/health");
        assertEquals(200, health.statusCode());
        assertEquals("UP", json(health).path("status").asText());
    }

    @Test void spaRoutesForwardToBuiltIndexButUnknownAssetsDoNot() throws Exception {
        Client client = client();
        HttpResponse<String> login = client.get("/login");
        assertEquals(200, login.statusCode(), login.body());
        assertTrue(login.headers().firstValue("content-type").orElse("").contains("text/html"));
        assertTrue(login.body().toLowerCase().contains("<html"));
        HttpResponse<String> dashboard = client.get("/dashboard");
        assertEquals(200, dashboard.statusCode());
        assertTrue(dashboard.body().toLowerCase().contains("<html"));
        assertEquals(404, client.get("/assets/missing-test-asset.js").statusCode());
    }

    private void insertTransaction(long userId, String type, String amount, String before, String after,
                                   String description, long actorId, String operationId, LocalDateTime createdAt) {
        long cardId = jdbc.queryForObject("SELECT id FROM campus_cards WHERE user_id=?", Long.class, userId);
        jdbc.update("INSERT INTO transaction_records(card_id,type,amount,balance_before,balance_after,description,actor_id,operation_id,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                cardId, type, amount, before, after, description, actorId, operationId, java.sql.Timestamp.valueOf(createdAt));
    }

    private long userId(String username) {
        return jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username);
    }

    private Client loggedIn(String username, String password) throws Exception {
        Client client = client();
        HttpResponse<String> login = client.login(username, password);
        assertEquals(200, login.statusCode(), login.body());
        client.userId = json(login).path("id").asLong();
        assertTrue(client.userId > 0);
        client.refreshCsrf();
        return client;
    }

    private Client client() { return new Client("http://127.0.0.1:" + port); }

    private static String body(Object... pairs) {
        var object = JSON.createObjectNode();
        for (int i = 0; i < pairs.length; i += 2) {
            Object value = pairs[i + 1];
            if (value instanceof Boolean b) object.put((String)pairs[i], b);
            else object.put((String)pairs[i], String.valueOf(value));
        }
        return JSON.writeValueAsString(object);
    }

    private static JsonNode json(HttpResponse<String> response) {
        try { return JSON.readTree(response.body()); }
        catch (Exception e) { throw new AssertionError("Expected JSON response, status=" + response.statusCode(), e); }
    }

    private static boolean isSecureSessionCookie(String header) {
        return header.startsWith("CAMPUS_SESSION=") && header.toLowerCase().contains("httponly")
                && header.toLowerCase().contains("samesite=lax");
    }

    private static final class Client {
        private final URI base;
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient http;
        private Csrf csrf;
        private long userId;

        Client(String base) {
            this.base = URI.create(base);
            http = HttpClient.newBuilder().cookieHandler(cookies).build();
        }

        HttpResponse<String> get(String path) throws Exception { return send("GET", path, null, null); }
        HttpResponse<String> post(String path, String body, Csrf token) throws Exception { return send("POST", path, body, token); }
        HttpResponse<String> postWithFreshCsrf(String path, String body) throws Exception { refreshCsrf(); return postWithFreshCsrf(path, body, "POST"); }
        HttpResponse<String> postWithFreshCsrf(String path, String body, String method) throws Exception {
            refreshCsrf(); return send(method, path, body, csrf);
        }

        HttpResponse<String> login(String username, String password) throws Exception {
            Csrf token = getCsrf();
            return post("/api/auth/login", body("username", username, "password", password), token);
        }

        Csrf getCsrf() throws Exception {
            HttpResponse<String> response = get("/api/auth/csrf");
            assertEquals(200, response.statusCode(), response.body());
            JsonNode json = json(response);
            Csrf token = new Csrf(json.path("token").asText(), json.path("headerName").asText());
            if (csrf == null || !csrf.token().equals(token.token())) csrf = token;
            return token;
        }

        void refreshCsrf() throws Exception { csrf = getCsrf(); }

        String sessionId() {
            return cookies.getCookieStore().getCookies().stream().filter(cookie -> "CAMPUS_SESSION".equals(cookie.getName()))
                    .map(HttpCookie::getValue).findFirst().orElse(null);
        }

        HttpResponse<String> send(String method, String path, String body, Csrf token) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path));
            if (body != null) request.header("Content-Type", "application/json");
            if (token != null) request.header(token.headerName(), token.token());
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
    }

    private record Csrf(String token, String headerName) { }

    public static final class TestDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override public void initialize(ConfigurableApplicationContext context) {
            String testUrl = requiredEnvironment("CC_TEST_DB_URL");
            String testUser = requiredEnvironment("CC_TEST_DB_USER");
            String testPassword = requiredEnvironment("CC_TEST_DB_PASSWORD", true);
            String configuredUrl = requiredEnvironment("CC_DB_URL");
            String configuredUser = requiredEnvironment("CC_DB_USER");
            String configuredPassword = requiredEnvironment("CC_DB_PASSWORD", true);
            if (!testUrl.equals(configuredUrl) || !testUser.equals(configuredUser) || !testPassword.equals(configuredPassword))
                throw new IllegalStateException("CC_DB_* must exactly match CC_TEST_DB_* before starting HTTP integration tests");
            String database = databaseName(testUrl);
            if (!database.endsWith("_test")) throw new IllegalStateException("CC_TEST_DB_URL must select a database whose name ends in _test");
            try (Connection connection = DriverManager.getConnection(testUrl, testUser, testPassword);
                 Statement statement = connection.createStatement();
                 ResultSet selected = statement.executeQuery("SELECT DATABASE()")) {
                if (!selected.next() || !database.equals(selected.getString(1)) || !selected.getString(1).endsWith("_test"))
                    throw new IllegalStateException("MySQL selected a database outside the *_test namespace");
                applySchema(connection);
            } catch (Exception exception) {
                if (exception instanceof IllegalStateException state) throw state;
                throw new IllegalStateException("Unable to prepare the isolated HTTP test database", exception);
            }
        }

        private static String requiredEnvironment(String key) {
            return requiredEnvironment(key, false);
        }

        private static String requiredEnvironment(String key, boolean allowBlank) {
            String value = System.getenv(key);
            if (value == null || (!allowBlank && value.isBlank()))
                throw new IllegalStateException(key + " is required for HTTP integration tests");
            return value;
        }

        private static String databaseName(String jdbcUrl) {
            String base = jdbcUrl.substring(0, jdbcUrl.indexOf('?') < 0 ? jdbcUrl.length() : jdbcUrl.indexOf('?'));
            int slash = base.lastIndexOf('/');
            if (!base.startsWith("jdbc:mysql:") || slash < "jdbc:mysql://".length() || slash == base.length() - 1)
                throw new IllegalStateException("CC_TEST_DB_URL must select a MySQL database");
            return base.substring(slash + 1);
        }

        private static void applySchema(Connection connection) throws IOException, java.sql.SQLException {
            Path schema = Path.of("database", "schema.sql");
            if (!Files.isRegularFile(schema)) schema = Path.of("..", "database", "schema.sql");
            String contents = Files.readString(schema, StandardCharsets.UTF_8);
            for (String sql : contents.split(";")) if (!sql.isBlank()) {
                try (Statement statement = connection.createStatement()) { statement.execute(sql); }
            }
        }
    }
}
