package com.campuscard.server.web;

import com.campuscard.api.ApplicationServices;
import com.campuscard.dto.CreateUserRequest;
import com.campuscard.dto.Page;
import com.campuscard.dto.SessionContext;
import com.campuscard.dto.UpdateUserRequest;
import com.campuscard.dto.UserFilter;
import com.campuscard.dto.Statistics;
import com.campuscard.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final ApplicationServices services;
    public AdminController(ApplicationServices services) { this.services = services; }

    @GetMapping("/users")
    public WebDtos.PageJson<User> users(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Boolean enabled,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int pageSize,
                                       HttpServletRequest request) {
        Page<User> result = services.admin().queryUsers(WebSession.coreContext(request),
                new UserFilter(keyword, enabled, page, pageSize));
        return WebDtos.PageJson.map(result, user -> user);
    }

    @PostMapping("/users")
    public ResponseEntity<User> createUser(@Valid @RequestBody WebDtos.CreateUserRequest body,
                                           HttpServletRequest request) {
        User user = services.admin().createUser(WebSession.coreContext(request),
                new CreateUserRequest(body.username(), body.password(), body.fullName(), body.studentNo()));
        return ResponseEntity.status(201).body(user);
    }

    @PutMapping("/users/{id}")
    public User updateUser(@PathVariable long id, @Valid @RequestBody WebDtos.UpdateUserRequest body,
                           HttpServletRequest request) {
        return services.admin().updateUser(WebSession.coreContext(request),
                new UpdateUserRequest(id, body.fullName(), body.studentNo()));
    }

    @PatchMapping("/users/{id}/enabled")
    public ResponseEntity<Void> setEnabled(@PathVariable long id,
                                           @Valid @RequestBody WebDtos.EnabledRequest body,
                                           HttpServletRequest request) {
        services.admin().setEnabled(WebSession.coreContext(request), id, body.enabled());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/statistics")
    public WebDtos.StatisticsJson statistics(@RequestParam(required = false) LocalDate from,
                                             @RequestParam(required = false) LocalDate to,
                                             HttpServletRequest request) {
        Statistics result = services.statistics().summarize(WebSession.coreContext(request), from, to);
        return WebDtos.StatisticsJson.from(result);
    }
}
