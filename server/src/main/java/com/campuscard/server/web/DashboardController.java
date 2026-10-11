package com.campuscard.server.web;

import com.campuscard.api.ApplicationServices;
import com.campuscard.dao.UnitOfWork;
import com.campuscard.dto.SessionContext;
import com.campuscard.exception.BusinessException;
import com.campuscard.model.Role;
import com.campuscard.model.User;
import com.campuscard.server.contract.DashboardOverview;
import com.campuscard.server.contract.DashboardReader;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final ApplicationServices services;
    private final UnitOfWork unitOfWork;
    private final DashboardReader dashboardReader;

    public DashboardController(ApplicationServices services, UnitOfWork unitOfWork, DashboardReader dashboardReader) {
        this.services = services;
        this.unitOfWork = unitOfWork;
        this.dashboardReader = dashboardReader;
    }

    @GetMapping
    public DashboardOverview dashboard(@RequestParam(defaultValue = "7") int days, HttpServletRequest request) {
        if (days < 1 || days > 90) throw new BusinessException("VALIDATION", "统计天数须为1至90天");
        SessionContext session = WebSession.coreContext(request);
        User user = services.auth().currentUser(session);
        LocalDate to = LocalDate.now(SHANGHAI);
        LocalDate from = to.minusDays(days - 1L);
        Long userId = user.role() == Role.USER ? user.id() : null;
        return unitOfWork.read(connection -> dashboardReader.summarize(connection, userId, from, to));
    }
}
