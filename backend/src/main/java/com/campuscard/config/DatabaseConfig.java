package com.campuscard.config;

import com.campuscard.exception.BusinessException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record DatabaseConfig(String url, String user, String password) {
    public static DatabaseConfig load() {
        Properties properties = new Properties();
        Path file = Path.of(System.getProperty("campus.config", "config/application.properties"));
        try {
            if (Files.isRegularFile(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(reader); }
            } else {
                try (var in = DatabaseConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
                    if (in != null) try (Reader reader = new java.io.InputStreamReader(in, StandardCharsets.UTF_8)) { properties.load(reader); }
                }
            }
        } catch (IOException e) {
            throw new BusinessException("DATABASE_ERROR", "读取数据库配置失败", e);
        }
        String url = value("CC_DB_URL", properties, "db.url");
        String user = value("CC_DB_USER", properties, "db.user");
        String password = value("CC_DB_PASSWORD", properties, "db.password");
        if (url == null || url.isBlank() || user == null || password == null)
            throw new BusinessException("DATABASE_ERROR", "数据库配置不完整，请设置 CC_DB_URL、CC_DB_USER 和 CC_DB_PASSWORD");
        return new DatabaseConfig(url.trim(), user.trim(), password);
    }

    private static String value(String env, Properties properties, String key) {
        String override = System.getenv(env);
        return override != null ? override : properties.getProperty(key);
    }
}
