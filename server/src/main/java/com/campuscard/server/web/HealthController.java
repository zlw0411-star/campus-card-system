package com.campuscard.server.web;

import com.campuscard.dao.ConnectionProvider;
import com.campuscard.exception.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final ConnectionProvider connections;
    public HealthController(ConnectionProvider connections) { this.connections = connections; }

    @GetMapping
    public WebDtos.StatusJson health() {
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             ResultSet ignored = statement.executeQuery("SELECT 1")) {
            if (!ignored.next() || ignored.getInt(1) != 1) throw new SQLException("Health check failed");
            return new WebDtos.StatusJson("UP");
        } catch (SQLException exception) {
            throw new BusinessException("DATABASE_ERROR", "数据库暂不可用", exception);
        }
    }
}
