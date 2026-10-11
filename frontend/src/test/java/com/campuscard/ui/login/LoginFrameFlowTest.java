package com.campuscard.ui.login;

import com.campuscard.api.*;
import com.campuscard.dto.*;
import com.campuscard.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real Swing flow by dispatching component actions on the EDT. */
class LoginFrameFlowTest {
    private final List<LoginFrame> frames = new ArrayList<>();

    @AfterEach void closeFrames() throws Exception {
        onEdt(() -> { for (LoginFrame frame : frames) frame.dispose(); });
    }

    @Test @Timeout(20) void adminOpensTheDefaultAllUsersList() throws Exception {
        CountDownLatch queried = new CountDownLatch(1);
        AtomicReference<UserFilter> observed = new AtomicReference<>();
        FakeApplication app = new FakeApplication(Role.ADMIN) {
            @Override public Page<User> queryUsers(SessionContext session, UserFilter filter) {
                observed.set(filter); queried.countDown(); return new Page<>(List.of(), 0, filter.page(), filter.pageSize());
            }
        };
        LoginFrame frame = createFrame(app.services());
        login(frame, "admin", "admin-password");

        assertTrue(queried.await(8, TimeUnit.SECONDS), "admin's default user list should load");
        assertNull(observed.get().enabled(), "the initial state filter means all enabled states");
        assertTrue(await(() -> onEdtValue(() -> tabTitles(frame).contains("用户管理"))));
    }

    @Test @Timeout(25) void rechargeRefreshesBalanceAndOldSessionCallbackCannotTouchNewSession() throws Exception {
        FakeApplication app = new FakeApplication(Role.USER);
        LoginFrame frame = createFrame(app.services());
        login(frame, "student", "student-password");

        assertTrue(await(() -> onEdtValue(() -> "¥ 100.00".equals(balanceText(frame)))));
        JPanel form = onEdtValue(() -> titledPanel(frame, "充值与消费"));
        List<JTextField> formFields = onEdtValue(() -> descendants(form, JTextField.class));
        assertEquals(2, formFields.size());
        JTextField amount = formFields.get(0);
        JTextField description = formFields.get(1);
        JButton recharge = onEdtValue(() -> button(frame, "充值"));

        onEdt(() -> { amount.setText("25.00"); description.setText("test top-up"); recharge.doClick(); });
        assertTrue(await(() -> onEdtValue(() -> "¥ 125.00".equals(balanceText(frame)))), "successful recharge should reload the card balance");
        assertTrue(app.cards.cardReadsFor("session-1") >= 2, "initial load and post-recharge refresh should use the first session");

        CountDownLatch oldWriteStarted = app.cards.blockNextSmallRecharge;
        onEdt(() -> { amount.setText("9.00"); description.setText("pending before logout"); recharge.doClick(); });
        assertTrue(oldWriteStarted.await(5, TimeUnit.SECONDS), "the second recharge should be in flight");
        assertNotNull(pendingOperation(frame));

        onEdt(() -> button(frame, "退出登录").doClick());
        assertNull(pendingOperation(frame), "logout must clear the uncertain operation");
        login(frame, "student", "student-password");
        assertTrue(await(() -> onEdtValue(() -> "¥ 500.00".equals(balanceText(frame)))), "the new session should load its own card state");
        assertEquals("session-2", app.auth.lastCurrentUserSession.get());

        app.cards.releaseOldWrite.countDown();
        assertTrue(app.cards.oldWriteReturned.await(5, TimeUnit.SECONDS));
        Thread.sleep(450); // allow SwingWorker.done to reach the EDT after the background service returns
        onEdt(() -> { });
        assertEquals(1, app.cards.cardReadsFor("session-2"), "the old callback must not refresh the new session's card");
        assertEquals("¥ 500.00", onEdtValue(() -> balanceText(frame)));
    }

    private LoginFrame createFrame(ApplicationServices services) throws Exception {
        AtomicReference<LoginFrame> frame = new AtomicReference<>();
        onEdt(() -> {
            LoginFrame value = new LoginFrame(services);
            value.setVisible(false);
            value.validate();
            value.doLayout();
            frame.set(value);
        });
        frames.add(frame.get());
        return frame.get();
    }

    private static void login(LoginFrame frame, String username, String password) throws Exception {
        onEdt(() -> {
            JTextField name = descendants(frame.getContentPane(), JTextField.class).stream()
                    .filter(field -> field.getClass() == JTextField.class).findFirst().orElseThrow();
            JPasswordField pass = descendants(frame.getContentPane(), JPasswordField.class).get(0);
            name.setText(username); pass.setText(password); button(frame, "登 录").doClick();
        });
    }

    private static List<String> tabTitles(LoginFrame frame) {
        JTabbedPane tabs = descendants(frame.getContentPane(), JTabbedPane.class).stream().findFirst().orElse(null);
        if (tabs == null) return List.of();
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) titles.add(tabs.getTitleAt(i));
        return titles;
    }

    private static JPanel titledPanel(LoginFrame frame, String title) {
        return descendants(frame.getContentPane(), JPanel.class).stream().filter(panel -> hasTitle(panel.getBorder(), title)).findFirst().orElseThrow();
    }

    private static boolean hasTitle(javax.swing.border.Border border, String title) {
        if (border instanceof TitledBorder titled) return title.equals(titled.getTitle());
        if (border instanceof javax.swing.border.CompoundBorder compound)
            return hasTitle(compound.getOutsideBorder(), title) || hasTitle(compound.getInsideBorder(), title);
        return false;
    }

    private static String balanceText(LoginFrame frame) {
        return descendants(frame.getContentPane(), JLabel.class).stream().map(JLabel::getText)
                .filter(text -> text != null && text.startsWith("¥ ")).findFirst().orElse(null);
    }

    private static JButton button(LoginFrame frame, String text) {
        return descendants(frame.getContentPane(), JButton.class).stream().filter(item -> text.equals(item.getText())).findFirst().orElseThrow();
    }

    private static Object pendingOperation(LoginFrame frame) throws Exception {
        Field field = LoginFrame.class.getDeclaredField("pendingOperation"); field.setAccessible(true); return field.get(frame);
    }

    private static <T extends Component> List<T> descendants(Container root, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component component : root.getComponents()) {
            if (type.isInstance(component)) result.add(type.cast(component));
            if (component instanceof Container child) result.addAll(descendants(child, type));
        }
        return result;
    }

    private static boolean await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < end) {
            if (condition.getAsBoolean()) return true;
            Thread.sleep(20);
        }
        return condition.getAsBoolean();
    }

    private static <T> T onEdtValue(Callable<T> action) {
        AtomicReference<T> result = new AtomicReference<>();
        try { onEdt(() -> { try { result.set(action.call()); } catch (Exception e) { throw new RuntimeException(e); } }); }
        catch (Exception e) { throw new RuntimeException(e); }
        return result.get();
    }

    private static void onEdt(Runnable action) throws Exception {
        if (EventQueue.isDispatchThread()) action.run(); else EventQueue.invokeAndWait(action);
    }

    private static class FakeApplication {
        final FakeAuth auth;
        final FakeCards cards = new FakeCards();
        final Role role;
        FakeApplication(Role role) { this.role = role; this.auth = new FakeAuth(role); }
        Page<User> queryUsers(SessionContext session, UserFilter filter) { return new Page<>(List.of(), 0, filter.page(), filter.pageSize()); }
        ApplicationServices services() {
            AdminService admin = new AdminService() {
                @Override public Page<User> queryUsers(SessionContext session, UserFilter filter) { return FakeApplication.this.queryUsers(session, filter); }
                @Override public User createUser(SessionContext session, CreateUserRequest request) { throw new UnsupportedOperationException(); }
                @Override public User updateUser(SessionContext session, UpdateUserRequest request) { throw new UnsupportedOperationException(); }
                @Override public void setEnabled(SessionContext session, long userId, boolean enabled) { throw new UnsupportedOperationException(); }
            };
            RecordService records = new RecordService() {
                @Override public Page<TransactionRecord> query(SessionContext session, TransactionFilter filter) { return new Page<>(List.of(), 0, filter.page(), filter.pageSize()); }
                @Override public String exportCsv(SessionContext session, TransactionFilter filter) { return "\uFEFF"; }
            };
            StatisticsService stats = (session, from, to) -> new Statistics(1, 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
            return new ApplicationServices(auth, cards, records, admin, stats);
        }
    }

    private static final class FakeAuth implements AuthService {
        final Role role;
        final AtomicInteger logins = new AtomicInteger();
        final AtomicReference<String> lastCurrentUserSession = new AtomicReference<>();
        FakeAuth(Role role) { this.role = role; }
        @Override public SessionContext login(String username, char[] password) {
            return new SessionContext("session-" + logins.incrementAndGet(), 5, role, "Test User");
        }
        @Override public void logout(SessionContext session) { }
        @Override public User currentUser(SessionContext session) {
            lastCurrentUserSession.set(session.token());
            return new User(5, "test", "Test User", "T-5", role, true, LocalDateTime.now());
        }
        @Override public void changePassword(SessionContext session, char[] oldPassword, char[] newPassword) { }
    }

    private static final class FakeCards implements CardService {
        final AtomicReference<BigDecimal> firstSessionBalance = new AtomicReference<>(new BigDecimal("100.00"));
        final ConcurrentMap<String, AtomicInteger> reads = new ConcurrentHashMap<>();
        final CountDownLatch blockNextSmallRecharge = new CountDownLatch(1);
        final CountDownLatch releaseOldWrite = new CountDownLatch(1);
        final CountDownLatch oldWriteReturned = new CountDownLatch(1);
        @Override public CampusCard getCard(SessionContext session) {
            reads.computeIfAbsent(session.token(), ignored -> new AtomicInteger()).incrementAndGet();
            BigDecimal value = session.token().equals("session-1") ? firstSessionBalance.get() : new BigDecimal("500.00");
            return new CampusCard(1, 5, "CARD-5", value);
        }
        @Override public TransactionRecord recharge(SessionContext session, BigDecimal amount, String description, String operationId) {
            if (amount.compareTo(new BigDecimal("9.00")) == 0) {
                blockNextSmallRecharge.countDown();
                try { if (!releaseOldWrite.await(8, TimeUnit.SECONDS)) throw new IllegalStateException("test did not release pending recharge"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            }
            BigDecimal before = firstSessionBalance.get();
            BigDecimal after = before.add(amount);
            firstSessionBalance.set(after);
            if (amount.compareTo(new BigDecimal("9.00")) == 0) oldWriteReturned.countDown();
            return new TransactionRecord(1, 1, 5, "test", "Test User", TransactionType.RECHARGE,
                    amount, before, after, description, 5, operationId, LocalDateTime.now());
        }
        @Override public TransactionRecord consume(SessionContext session, BigDecimal amount, String description, String operationId) {
            throw new UnsupportedOperationException();
        }
        @Override public Optional<TransactionRecord> findOperation(SessionContext session, String operationId) { return Optional.empty(); }
        int cardReadsFor(String token) { return Optional.ofNullable(reads.get(token)).map(AtomicInteger::get).orElse(0); }
    }
}
