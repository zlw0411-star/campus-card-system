package com.campuscard;
import com.campuscard.ui.login.LoginFrame;
import javax.swing.*;
import java.util.Arrays;
public final class Main {
    private Main() {}
    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--check-db")) {
            try { System.out.println("数据库连接成功，MySQL " + Bootstrap.checkConnection()); }
            catch (Exception e) { System.err.println("数据库连接失败，请检查配置、服务与初始化状态。" + e.getMessage()); System.exit(1); }
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                for (var info : UIManager.getInstalledLookAndFeels()) if ("Nimbus".equals(info.getName())) { UIManager.setLookAndFeel(info.getClassName()); break; }
                var font = new java.awt.Font("Microsoft YaHei", java.awt.Font.PLAIN, 14);
                for (var key : java.util.Collections.list(UIManager.getDefaults().keys())) if (UIManager.get(key) instanceof javax.swing.plaf.FontUIResource) UIManager.put(key, new javax.swing.plaf.FontUIResource(font));
                new LoginFrame(Bootstrap.create()).setVisible(true);
            } catch (Exception e) { JOptionPane.showMessageDialog(null, "启动失败，请检查配置文件。\n" + e.getMessage(), "校园卡管理", JOptionPane.ERROR_MESSAGE); }
        });
    }
}