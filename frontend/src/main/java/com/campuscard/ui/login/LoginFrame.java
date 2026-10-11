package com.campuscard.ui.login;

import com.campuscard.api.ApplicationServices;
import com.campuscard.dto.*;
import com.campuscard.exception.BusinessException;
import com.campuscard.model.*;
import com.campuscard.ui.components.SessionWorker;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

/** Swing entry point for the campus card application. */
public final class LoginFrame extends JFrame {
    private static final Color NAVY = new Color(28, 48, 78);
    private static final Color BLUE = new Color(42, 103, 183);
    private static final Color BG = new Color(245, 247, 250);
    private final ApplicationServices services;
    private final CardLayout rootLayout = new CardLayout();
    private final JPanel root = new JPanel(rootLayout);
    private final JLabel loginStatus = new JLabel(" ");
    private JTextField usernameField;
    private JPasswordField passwordField;
    private volatile boolean disposed;
    private volatile long generation;
    private volatile SessionContext session;
    private User currentUser;

    public LoginFrame(ApplicationServices services) {
        super("校园卡管理系统");
        this.services = Objects.requireNonNull(services, "services");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(860, 600));
        setSize(1080, 720);
        setLocationRelativeTo(null);
        setContentPane(root);
        root.add(buildLogin(), "login");
        rootLayout.show(root, "login");
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                disposed = true;
                generation++;
                SessionContext old = session;
                session = null; pendingOperation = null;
                if (old != null) new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() { services.auth().logout(old); return null; }
                }.execute();
            }
        });
    }

    private JPanel buildLogin() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(BG);
        JPanel card = new JPanel(new BorderLayout(0, 22));
        card.setBorder(new EmptyBorder(38, 44, 34, 44));
        card.setBackground(Color.WHITE);
        card.setPreferredSize(new Dimension(430, 410));
        JLabel title = new JLabel("校园卡管理系统", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        title.setForeground(NAVY);
        JLabel subtitle = new JLabel("Campus Card Services", SwingConstants.CENTER);
        subtitle.setForeground(new Color(105, 117, 133));
        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 8));
        heading.setOpaque(false); heading.add(title); heading.add(subtitle);
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 10)); form.setOpaque(false);
        form.add(label("用户名")); usernameField = new JTextField(); form.add(usernameField);
        form.add(label("密码")); passwordField = new JPasswordField(); form.add(passwordField);
        JButton login = primaryButton("登 录");
        login.addActionListener(e -> login(login));
        passwordField.addActionListener(e -> login.doClick());
        JPanel bottom = new JPanel(new BorderLayout(0, 8)); bottom.setOpaque(false);
        bottom.add(login, BorderLayout.NORTH); loginStatus.setForeground(new Color(175, 55, 55)); bottom.add(loginStatus, BorderLayout.CENTER);
        card.add(heading, BorderLayout.NORTH); card.add(form, BorderLayout.CENTER); card.add(bottom, BorderLayout.SOUTH);
        outer.add(card); return outer;
    }

    private void login(JButton button) {
        String name = usernameField.getText().trim();
        char[] password = passwordField.getPassword();
        if (name.isEmpty() || password.length == 0) { Arrays.fill(password, '\0'); loginStatus.setText("请输入用户名和密码"); return; }
        long g = ++generation;
        button.setEnabled(false); loginStatus.setText("正在登录…");
        new SwingWorker<SessionContext, Void>() {
            @Override protected SessionContext doInBackground() {
                if (disposed || g != generation) return null;
                return services.auth().login(name, password);
            }
            @Override protected void done() {
                Arrays.fill(password, '\0');
                try {
                    SessionContext result = get();
                    if (disposed || g != generation) { if (result != null) logoutAsync(result); return; }
                    if (result == null) { button.setEnabled(true); loginStatus.setText("登录失败，请重试"); return; }
                    session = result;
                    loadMain(g, button);
                } catch (Exception ex) { if (!disposed && g == generation) { button.setEnabled(true); loginStatus.setText(userMessage(ex)); } }
            }
        }.execute();
    }

    private void loadMain(long g, JButton loginButton) {
        setBusy(true);
        SessionContext active = session;
        new SwingWorker<User, Void>() {
            @Override protected User doInBackground() { return services.auth().currentUser(active); }
            @Override protected void done() {
                if (disposed || g != generation) return;
                setBusy(false);
                try {
                    currentUser = get();
                    JPanel page = buildWorkspace();
                    root.add(page, "workspace"); rootLayout.show(root, "workspace");
                    revalidate(); repaint(); loginButton.setEnabled(true);
                } catch (Exception ex) {
                    SessionContext failedSession = session; session = null; currentUser = null; generation++;
                    loginButton.setEnabled(true); loginStatus.setText(userMessage(ex));
                    if (failedSession != null) logoutAsync(failedSession);
                }
            }
        }.execute();
    }

    private JPanel buildWorkspace() {
        JPanel shell = new JPanel(new BorderLayout()); shell.setBackground(BG);
        JPanel top = new JPanel(new BorderLayout(16, 0)); top.setBorder(new EmptyBorder(13, 22, 13, 22)); top.setBackground(NAVY);
        JLabel brand = new JLabel("校园卡管理系统"); brand.setForeground(Color.WHITE); brand.setFont(brand.getFont().deriveFont(Font.BOLD, 19f));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0)); right.setOpaque(false);
        JLabel who = new JLabel(currentUser.fullName() + "  ·  " + (currentUser.role() == Role.ADMIN ? "管理员" : "学生")); who.setForeground(Color.WHITE);
        JButton change = new JButton("修改密码"); change.addActionListener(e -> changePassword());
        JButton exit = new JButton("退出登录"); exit.addActionListener(e -> logout());
        right.add(who); right.add(change); right.add(exit); top.add(brand, BorderLayout.WEST); top.add(right, BorderLayout.EAST);
        JTabbedPane tabs = new JTabbedPane();
        if (currentUser.role() == Role.USER) {
            tabs.addTab("我的校园卡", userCardPanel());
            tabs.addTab("个人交易记录", historyPanel(true));
        } else {
            tabs.addTab("用户管理", adminUsersPanel());
            tabs.addTab("全站交易", historyPanel(false));
            tabs.addTab("运营统计", statsPanel());
        }
        shell.add(top, BorderLayout.NORTH); shell.add(tabs, BorderLayout.CENTER); return shell;
    }

    private JPanel userCardPanel() {
        JPanel body = pagePanel();
        JLabel balance = new JLabel("读取中…"); balance.setFont(balance.getFont().deriveFont(Font.BOLD, 32f)); balance.setForeground(BLUE);
        JLabel cardNo = new JLabel(" "); JLabel status = new JLabel(" ");
        JPanel summary = section("账户余额", new GridLayout(0, 1, 0, 10)); summary.add(balance); summary.add(cardNo);
        body.add(summary, BorderLayout.NORTH);
        JPanel actions = section("充值与消费", new GridBagLayout()); GridBagConstraints c = new GridBagConstraints(); c.insets = new Insets(7, 8, 7, 8); c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        JTextField amount = new JTextField(); JTextField desc = new JTextField();
        addField(actions, c, 0, "金额（元）", amount); addField(actions, c, 1, "说明", desc);
        JButton recharge = primaryButton("充值"); JButton consume = new JButton("模拟消费"); JButton refresh = new JButton("刷新余额");
        c.gridy = 2; c.gridx = 0; actions.add(recharge, c); c.gridx = 1; actions.add(consume, c); c.gridx = 2; actions.add(refresh, c);
        JButton resolve = new JButton("查询状态并安全重试"); resolve.setVisible(false);
        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT)); statusRow.setOpaque(false); statusRow.add(status); statusRow.add(resolve);
        c.gridy = 3; c.gridx = 0; c.gridwidth = 3; actions.add(statusRow, c);
        body.add(actions, BorderLayout.CENTER);
        Runnable load = () -> async(active -> services.cards().getCard(active), card -> { balance.setText("¥ " + card.balance().toPlainString()); cardNo.setText("卡号：" + card.cardNo()); }, ex -> status.setText(userMessage(ex)));
        refresh.addActionListener(e -> load.run());
        recharge.addActionListener(e -> submitMoney(amount, desc, true, status, recharge, consume, resolve, load));
        consume.addActionListener(e -> submitMoney(amount, desc, false, status, recharge, consume, resolve, load));
        resolve.addActionListener(e -> resolveOperation(status, amount, desc, recharge, consume, resolve, load));
        load.run(); return body;
    }

    private static final class PendingOperation {
        final String id = UUID.randomUUID().toString();
        final BigDecimal amount; final String description; final boolean recharge;
        PendingOperation(BigDecimal amount, String description, boolean recharge) { this.amount = amount; this.description = description; this.recharge = recharge; }
    }
    private PendingOperation pendingOperation;

    private void submitMoney(JTextField amountField, JTextField descriptionField, boolean recharge, JLabel status, JButton rechargeButton, JButton consumeButton, JButton resolveButton, Runnable refreshBalance) {
        if (pendingOperation != null) {
            if (pendingOperation.recharge != recharge) { status.setText("请先处理上一次交易状态"); return; }
            // An uncertain request must be looked up before the same UUID can be submitted again.
            resolveOperation(status, amountField, descriptionField, rechargeButton, consumeButton, resolveButton, refreshBalance);
            return;
        }
        if (pendingOperation == null) {
            try {
                String raw = amountField.getText().trim();
                if (!raw.matches("[0-9]+(?:\\.[0-9]{1,2})?")) throw new IllegalArgumentException("金额须为正数且最多两位小数");
                BigDecimal amount = new BigDecimal(raw);
                if (amount.signum() <= 0 || amount.compareTo(new BigDecimal("9999999999.99")) > 0) throw new IllegalArgumentException("金额超出允许范围");
                String desc = descriptionField.getText().trim();
                if (!recharge && desc.isEmpty()) throw new IllegalArgumentException("请填写消费说明");
                pendingOperation = new PendingOperation(amount, desc, recharge);
            } catch (RuntimeException ex) { status.setText(ex.getMessage()); return; }
        } else if (pendingOperation.recharge != recharge) {
            status.setText("请先处理上一次交易状态"); return;
        }
        PendingOperation op = pendingOperation;
        rechargeButton.setEnabled(false); consumeButton.setEnabled(false); amountField.setEnabled(false); descriptionField.setEnabled(false); status.setText("正在提交交易…");
        async(active -> writeOperation(active, op), record -> {
            pendingOperation = null; rechargeButton.setEnabled(true); consumeButton.setEnabled(true);
            amountField.setEnabled(true); descriptionField.setEnabled(true); resolveButton.setVisible(false);
            status.setText("交易成功，余额：¥" + record.balanceAfter().toPlainString()); amountField.setText(""); descriptionField.setText(""); refreshBalance.run();
        }, ex -> {
            if (isDatabaseError(ex)) {
                rechargeButton.setEnabled(false); consumeButton.setEnabled(false); amountField.setEnabled(false); descriptionField.setEnabled(false);
                resolveButton.setVisible(true); resolveButton.setEnabled(true); status.setText("交易结果待确认，请查询状态。");
            } else { pendingOperation = null; rechargeButton.setEnabled(true); consumeButton.setEnabled(true); amountField.setEnabled(true); descriptionField.setEnabled(true); resolveButton.setVisible(false); status.setText(userMessage(ex)); }
        });
    }

    private TransactionRecord writeOperation(SessionContext active, PendingOperation op) {
        return op.recharge ? services.cards().recharge(active, op.amount, op.description, op.id)
                : services.cards().consume(active, op.amount, op.description, op.id);
    }

    private void resolveOperation(JLabel status, JTextField amountField, JTextField descField, JButton recharge, JButton consume, JButton check, Runnable refreshBalance) {
        PendingOperation op = pendingOperation;
        if (op == null) return;
        check.setEnabled(false); status.setText("正在查询交易状态…");
        async(active -> services.cards().findOperation(active, op.id), found -> {
            if (found.isPresent()) {
                TransactionRecord r = found.get(); pendingOperation = null;
                status.setText("交易已完成，余额：¥" + r.balanceAfter().toPlainString()); amountField.setText(""); descField.setText("");
                amountField.setEnabled(true); descField.setEnabled(true); recharge.setEnabled(true); consume.setEnabled(true); check.setVisible(false); refreshBalance.run();
            } else {
                status.setText("未找到该交易，正在使用原请求重试…");
                async(activeSession -> writeOperation(activeSession, op), r -> {
                    pendingOperation = null; status.setText("交易成功，余额：¥" + r.balanceAfter().toPlainString()); amountField.setText(""); descField.setText("");
                    amountField.setEnabled(true); descField.setEnabled(true); recharge.setEnabled(true); consume.setEnabled(true); check.setVisible(false); refreshBalance.run();
                }, err -> {
                    check.setEnabled(true);
                    if (isDatabaseError(err)) status.setText("结果仍待确认，请再次查询状态。");
                    else { pendingOperation = null; amountField.setEnabled(true); descField.setEnabled(true); recharge.setEnabled(true); consume.setEnabled(true); check.setVisible(false); status.setText(userMessage(err)); }
                });
            }
        }, err -> { status.setText("无法确认交易状态，请稍后重新查询。"); check.setEnabled(true); });
    }

    private JPanel historyPanel(boolean personal) {
        JPanel body = pagePanel();
        JPanel filters = new JPanel(new GridLayout(2, 1, 0, 2)); filters.setOpaque(false);
        JPanel filterRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2)); filterRow1.setOpaque(false);
        JPanel filterRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2)); filterRow2.setOpaque(false);
        JComboBox<String> type = new JComboBox<>(new String[]{"全部", "充值", "消费"});
        JTextField from = new JTextField(10), to = new JTextField(10), userId = new JTextField(8);
        from.setToolTipText("日期格式：YYYY-MM-DD"); to.setToolTipText("日期格式：YYYY-MM-DD");
        filterRow1.add(new JLabel("类型")); filterRow1.add(type);
        if (!personal) { filterRow1.add(new JLabel("用户ID")); filterRow1.add(userId); }
        JButton query = primaryButton("查询"); JButton export = new JButton("导出当前筛选 CSV");
        filterRow2.add(new JLabel("开始日期")); filterRow2.add(from); filterRow2.add(new JLabel("结束日期")); filterRow2.add(to); filterRow2.add(query); filterRow2.add(export);
        filters.add(filterRow1); filters.add(filterRow2);
        DefaultTableModel model = tableModel("时间", "用户", "类型", "金额", "交易后余额", "说明"); JTable table = new JTable(model); styleTable(table);
        JLabel status = new JLabel(" "); JPanel lower = new JPanel(new BorderLayout()); lower.setOpaque(false);
        JButton prev = new JButton("上一页"), next = new JButton("下一页"); JLabel pageLabel = new JLabel("第 1 页"); JPanel pager = new JPanel(new FlowLayout(FlowLayout.RIGHT)); pager.setOpaque(false); pager.add(prev); pager.add(pageLabel); pager.add(next); lower.add(status, BorderLayout.WEST); lower.add(pager, BorderLayout.EAST);
        body.add(filters, BorderLayout.NORTH); body.add(new JScrollPane(table), BorderLayout.CENTER); body.add(lower, BorderLayout.SOUTH);
        final int[] page = {1}, pages = {1};
        java.util.function.BiConsumer<Integer, Boolean> load = (p, showMsg) -> {
            try {
                LocalDate f = parseDate(from.getText()), t = parseDate(to.getText());
                Long uid = null;
                if (!personal && !userId.getText().trim().isEmpty()) uid = Long.parseLong(userId.getText().trim());
                TransactionType tx = type.getSelectedIndex() == 1 ? TransactionType.RECHARGE : type.getSelectedIndex() == 2 ? TransactionType.CONSUMPTION : null;
                TransactionFilter filter = new TransactionFilter(uid, tx, f, t, p, 20);
                query.setEnabled(false); prev.setEnabled(false); next.setEnabled(false); status.setText("正在查询…");
                async(active -> services.records().query(active, filter), result -> {
                    page[0] = result.page(); pages[0] = Math.max(1, (int)((result.total() + result.pageSize() - 1) / result.pageSize()));
                    model.setRowCount(0); for (TransactionRecord r : result.items()) model.addRow(new Object[]{r.createdAt(), r.fullName(), r.type() == TransactionType.RECHARGE ? "充值" : "消费", r.amount(), r.balanceAfter(), r.description()});
                    pageLabel.setText("第 " + page[0] + " / " + pages[0] + " 页，共 " + result.total() + " 条"); status.setText(" "); query.setEnabled(true); prev.setEnabled(page[0] > 1); next.setEnabled(page[0] < pages[0]);
                }, ex -> { status.setText(userMessage(ex)); query.setEnabled(true); prev.setEnabled(page[0] > 1); next.setEnabled(page[0] < pages[0]); });
            } catch (RuntimeException ex) { status.setText(ex.getMessage()); }
        };
        query.addActionListener(e -> load.accept(1, true)); prev.addActionListener(e -> load.accept(Math.max(1, page[0] - 1), true)); next.addActionListener(e -> load.accept(page[0] + 1, true));
        export.addActionListener(e -> exportCsv(personal, type, from, to, userId, status, export));
        load.accept(1, false); return body;
    }

    private void exportCsv(boolean personal, JComboBox<String> type, JTextField from, JTextField to, JTextField userId, JLabel status, JButton export) {
        JFileChooser chooser = new JFileChooser(); chooser.setDialogTitle("导出交易记录 CSV"); chooser.setSelectedFile(new java.io.File("校园卡交易记录.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File selectedFile = chooser.getSelectedFile();
        if (selectedFile.exists() && JOptionPane.showConfirmDialog(this, "文件已存在，是否覆盖？", "确认覆盖", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            LocalDate f = parseDate(from.getText()), t = parseDate(to.getText());
            Long uid = !personal && !userId.getText().trim().isEmpty() ? Long.parseLong(userId.getText().trim()) : null;
            TransactionType tx = type.getSelectedIndex() == 1 ? TransactionType.RECHARGE : type.getSelectedIndex() == 2 ? TransactionType.CONSUMPTION : null;
            TransactionFilter filter = new TransactionFilter(uid, tx, f, t, 1, 100);
            java.io.File file = selectedFile; status.setText("正在导出…"); export.setEnabled(false);
            async(active -> {
                String csv = services.records().exportCsv(active, filter);
                java.nio.file.Files.writeString(file.toPath(), csv, java.nio.charset.StandardCharsets.UTF_8);
                return file;
            }, saved -> { status.setText("已导出：" + saved.getName()); export.setEnabled(true); }, ex -> { status.setText("导出失败：" + userMessage(ex)); export.setEnabled(true); });
        } catch (RuntimeException ex) { status.setText(ex.getMessage()); }
    }

    private JPanel adminUsersPanel() {
        JPanel body = pagePanel(); JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT)); filters.setOpaque(false);
        JTextField keyword = new JTextField(18); JComboBox<String> enabled = new JComboBox<>(new String[]{"全部状态", "启用", "停用"}); JButton search = primaryButton("查询"); JButton create = new JButton("新建用户");
        filters.add(new JLabel("关键词")); filters.add(keyword); filters.add(enabled); filters.add(search); filters.add(create);
        DefaultTableModel model = tableModel("ID", "用户名", "姓名", "学号", "状态", "创建时间"); JTable table = new JTable(model); styleTable(table);
        JLabel status = new JLabel(" "); JButton prev = new JButton("上一页"), next = new JButton("下一页"), edit = new JButton("修改资料"), toggle = new JButton("启用 / 停用"); JLabel pageLabel = new JLabel("第 1 页");
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT)); controls.setOpaque(false); controls.add(status); controls.add(edit); controls.add(toggle); controls.add(prev); controls.add(pageLabel); controls.add(next);
        body.add(filters, BorderLayout.NORTH); body.add(new JScrollPane(table), BorderLayout.CENTER); body.add(controls, BorderLayout.SOUTH);
        final int[] page = {1}, pages = {1};
        Runnable[] load = new Runnable[1];
        load[0] = () -> {
            Boolean en = enabled.getSelectedIndex() == 1 ? Boolean.TRUE : enabled.getSelectedIndex() == 2 ? Boolean.FALSE : null;
            int requestedPage = page[0];
            UserFilter f = new UserFilter(keyword.getText().trim(), en, requestedPage, 20); search.setEnabled(false); prev.setEnabled(false); next.setEnabled(false); status.setText("正在查询…");
            async(active -> services.admin().queryUsers(active, f), result -> {
                model.setRowCount(0); for (User u : result.items()) model.addRow(new Object[]{u.id(), u.username(), u.fullName(), u.studentNo(), u.enabled() ? "启用" : "停用", u.createdAt()});
                pages[0] = Math.max(1, (int)((result.total()+19)/20)); pageLabel.setText("第 " + page[0] + " / " + pages[0] + " 页"); status.setText("共 " + result.total() + " 位用户"); search.setEnabled(true); prev.setEnabled(page[0] > 1); next.setEnabled(page[0] < pages[0]);
            }, ex -> { status.setText(userMessage(ex)); search.setEnabled(true); prev.setEnabled(page[0] > 1); next.setEnabled(page[0] < pages[0]); });
        };
        search.addActionListener(e -> { page[0] = 1; load[0].run(); }); prev.addActionListener(e -> { page[0]--; load[0].run(); }); next.addActionListener(e -> { page[0]++; load[0].run(); });
        create.addActionListener(e -> createUser(status, load[0]));
        edit.addActionListener(e -> { User u = selectedUser(table); if (u == null) return; editUser(u, status, load[0]); });
        toggle.addActionListener(e -> { User u = selectedUser(table); if (u == null) return; async(active -> { services.admin().setEnabled(active, u.id(), !u.enabled()); return null; }, v -> { status.setText("用户状态已更新"); load[0].run(); }, ex -> status.setText(userMessage(ex))); });
        load[0].run(); return body;
    }

    private User selectedUser(JTable table) {
        int row = table.getSelectedRow(); if (row < 0) { JOptionPane.showMessageDialog(this, "请先选择一位用户", "提示", JOptionPane.INFORMATION_MESSAGE); return null; }
        long id = ((Number)table.getValueAt(row, 0)).longValue();
        Object studentNo = table.getValueAt(row, 3);
        return new User(id, String.valueOf(table.getValueAt(row,1)), String.valueOf(table.getValueAt(row,2)), studentNo == null ? null : String.valueOf(studentNo), Role.USER, "启用".equals(table.getValueAt(row,4)), null);
    }

    private void createUser(JLabel status, Runnable reload) {
        JTextField username = new JTextField(), fullName = new JTextField(), studentNo = new JTextField(); JPasswordField password = new JPasswordField();
        Object[] fields = {"用户名", username, "初始密码", password, "姓名", fullName, "学号", studentNo};
        if (JOptionPane.showConfirmDialog(this, fields, "新建学生用户", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        char[] p = password.getPassword(); String pass = new String(p); Arrays.fill(p, '\0');
        CreateUserRequest request = new CreateUserRequest(username.getText().trim(), pass, fullName.getText().trim(), studentNo.getText().trim());
        async(active -> services.admin().createUser(active, request), u -> { status.setText("用户已创建"); reload.run(); }, ex -> status.setText(userMessage(ex)));
    }

    private void editUser(User u, JLabel status, Runnable reload) {
        JTextField fullName = new JTextField(u.fullName()), studentNo = new JTextField(u.studentNo() == null ? "" : u.studentNo());
        if (JOptionPane.showConfirmDialog(this, new Object[]{"姓名", fullName, "学号", studentNo}, "修改用户资料", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        UpdateUserRequest request = new UpdateUserRequest(u.id(), fullName.getText().trim(), studentNo.getText().trim());
        async(active -> services.admin().updateUser(active, request), v -> { status.setText("用户资料已更新"); reload.run(); }, ex -> status.setText(userMessage(ex)));
    }

    private JPanel statsPanel() {
        JPanel body = pagePanel(); JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT)); controls.setOpaque(false);
        JTextField from = new JTextField(10), to = new JTextField(10); JButton query = primaryButton("统计查询"); controls.add(new JLabel("开始日期")); controls.add(from); controls.add(new JLabel("结束日期")); controls.add(to); controls.add(query);
        JPanel values = new JPanel(new GridLayout(0, 2, 16, 16)); values.setOpaque(false); values.setBorder(new EmptyBorder(30, 60, 30, 60));
        JLabel users = metric(values, "学生用户数"), enabled = metric(values, "启用用户数"), balance = metric(values, "校园卡总余额"), recharge = metric(values, "日期范围充值"), consume = metric(values, "日期范围消费"), count = metric(values, "日期范围交易笔数");
        JLabel status = new JLabel(" "); body.add(controls, BorderLayout.NORTH); body.add(values, BorderLayout.CENTER); body.add(status, BorderLayout.SOUTH);
        query.addActionListener(e -> { try { LocalDate f=parseDate(from.getText()), t=parseDate(to.getText()); query.setEnabled(false); async(active -> services.statistics().summarize(active,f,t), s -> {
            users.setText(Long.toString(s.users())); enabled.setText(Long.toString(s.enabledUsers())); balance.setText("¥ " + s.totalBalance()); recharge.setText("¥ " + s.rechargeAmount()); consume.setText("¥ " + s.consumptionAmount()); count.setText(Long.toString(s.transactionCount())); status.setText(" "); query.setEnabled(true);
        }, ex -> { status.setText(userMessage(ex)); query.setEnabled(true); }); } catch(RuntimeException ex) { status.setText(ex.getMessage()); } });
        query.doClick(); return body;
    }

    private void changePassword() {
        JPasswordField old = new JPasswordField(), next = new JPasswordField();
        if (JOptionPane.showConfirmDialog(this, new Object[]{"当前密码", old, "新密码（8–128 位）", next}, "修改密码", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        char[] o=old.getPassword(), n=next.getPassword();
        async(active -> { services.auth().changePassword(active,o,n); return null; }, v -> JOptionPane.showMessageDialog(this,"密码已修改"), ex -> showError(ex), o, n);
    }

    private void logout() {
        SessionContext old = session; session = null; generation++; currentUser = null; pendingOperation = null;
        root.removeAll(); root.add(buildLogin(), "login"); rootLayout.show(root, "login"); revalidate(); repaint();
        if (old != null) new SwingWorker<Void,Void>() { @Override protected Void doInBackground() { services.auth().logout(old); return null; } }.execute();
    }

    private void logoutAsync(SessionContext old) { new SwingWorker<Void,Void>() { @Override protected Void doInBackground() { services.auth().logout(old); return null; } }.execute(); }

    private <T> void async(SessionWorker.Work<T> work, Consumer<T> success, Consumer<Exception> failure, char[]... secrets) {
        long g = generation; SessionContext captured = session;
        SessionWorker.execute(captured, () -> !disposed && g == generation && captured == session,
                work, success, failure, () -> { for (char[] s : secrets) Arrays.fill(s, '\0'); });
    }

    private void setBusy(boolean busy) { setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor()); }
    private void showError(Exception ex) { JOptionPane.showMessageDialog(this, userMessage(ex), "操作失败", JOptionPane.ERROR_MESSAGE); }
    private static boolean isDatabaseError(Throwable ex) { Throwable t=ex; while(t!=null) { if(t instanceof BusinessException b && "DATABASE_ERROR".equals(b.code())) return true; t=t.getCause(); } return false; }
    private static String userMessage(Throwable ex) {
        Throwable t=ex; while(t.getCause()!=null && !(t instanceof BusinessException)) t=t.getCause();
        if(t instanceof BusinessException b) return switch(b.code()) {
            case "AUTH_REQUIRED" -> "登录已失效，请重新登录"; case "LOGIN_FAILED" -> "用户名或密码不正确"; case "USER_DISABLED" -> "此账户已停用"; case "FORBIDDEN" -> "没有权限执行此操作";
            case "VALIDATION" -> b.getMessage()==null ? "输入内容不符合要求" : b.getMessage(); case "NOT_FOUND" -> "没有找到相关记录"; case "INSUFFICIENT_BALANCE" -> "余额不足，无法完成消费";
            case "DUPLICATE" -> "用户名或学号已存在"; case "OPERATION_CONFLICT" -> "交易请求与已有记录不一致，请联系管理员"; case "DATABASE_ERROR" -> "服务暂时不可用，交易状态可能需要确认"; default -> "操作失败，请检查输入后重试";
        };
        return t.getMessage()==null ? "操作失败，请稍后重试" : t.getMessage();
    }

    private static LocalDate parseDate(String text) {
        String s=text.trim(); if(s.isEmpty()) return null;
        try { return LocalDate.parse(s); } catch(DateTimeParseException e) { throw new IllegalArgumentException("日期请使用 YYYY-MM-DD 格式"); }
    }
    private static JPanel pagePanel() { JPanel p=new JPanel(new BorderLayout(0,14)); p.setBackground(BG); p.setBorder(new EmptyBorder(18,20,16,20)); return p; }
    private static JPanel section(String title, LayoutManager layout) { JPanel p=new JPanel(layout); p.setBackground(Color.WHITE); p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(title),new EmptyBorder(14,16,14,16))); return p; }
    private static JLabel label(String text) { JLabel l=new JLabel(text); l.setForeground(NAVY); return l; }
    private static JButton primaryButton(String text) { JButton b=new JButton(text); b.setBackground(BLUE); b.setForeground(Color.WHITE); b.setFocusPainted(false); return b; }
    private static void addField(JPanel p, GridBagConstraints c, int row, String title, JComponent input) { c.gridy=row; c.gridwidth=1; c.gridx=0; c.weightx=.25; p.add(new JLabel(title),c); c.gridx=1; c.gridwidth=2; c.weightx=1; p.add(input,c); }
    private static DefaultTableModel tableModel(String... columns) { return new DefaultTableModel(columns,0) { @Override public boolean isCellEditable(int r,int c) { return false; } }; }
    private static void styleTable(JTable t) { t.setRowHeight(27); t.setFillsViewportHeight(true); t.setAutoCreateRowSorter(true); }
    private static JLabel metric(JPanel p,String title) { JPanel cell=new JPanel(new BorderLayout(0,8)); cell.setBackground(Color.WHITE); cell.setBorder(new EmptyBorder(18,20,18,20)); JLabel name=new JLabel(title); JLabel value=new JLabel("—"); value.setFont(value.getFont().deriveFont(Font.BOLD,22f)); value.setForeground(BLUE); cell.add(name,BorderLayout.NORTH); cell.add(value,BorderLayout.CENTER); p.add(cell); return value; }
}
