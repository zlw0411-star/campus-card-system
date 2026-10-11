package com.campuscard.server.web;

import com.campuscard.api.ApplicationServices;
import com.campuscard.dto.SessionContext;
import com.campuscard.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final ApplicationServices services;
    private final CsrfTokenRepository csrfTokens;
    private final SecurityContextRepository securityContexts;

    public AuthController(ApplicationServices services, CsrfTokenRepository csrfTokens,
                          SecurityContextRepository securityContexts) {
        this.services = services;
        this.csrfTokens = csrfTokens;
        this.securityContexts = securityContexts;
    }

    @GetMapping("/csrf")
    public WebDtos.CsrfJson csrf(CsrfToken token) {
        return new WebDtos.CsrfJson(token.getToken(), token.getHeaderName());
    }

    @PostMapping("/login")
    public User login(@Valid @RequestBody WebDtos.LoginRequest body,
                      HttpServletRequest request, HttpServletResponse response) {
        char[] password = body.password().toCharArray();
        SessionContext authenticated;
        try { authenticated = services.auth().login(body.username(), password); }
        finally { java.util.Arrays.fill(password, '\0'); }

        HttpSession httpSession = request.getSession(true);
        SessionContext previous = (SessionContext) httpSession.getAttribute(WebSession.CORE_CONTEXT_ATTRIBUTE);
        if (previous != null) services.auth().logout(previous);
        request.changeSessionId();
        httpSession = request.getSession(false);
        httpSession.setAttribute(WebSession.CORE_CONTEXT_ATTRIBUTE, authenticated);

        User user = services.auth().currentUser(authenticated);
        var authentication = new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContexts.saveContext(context, request, response);
        csrfTokens.saveToken(null, request, response);
        return user;
    }

    @GetMapping("/me")
    public User me(HttpServletRequest request) {
        return services.auth().currentUser(WebSession.coreContext(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object value = session.getAttribute(WebSession.CORE_CONTEXT_ATTRIBUTE);
            if (value instanceof SessionContext context) services.auth().logout(context);
        }
        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody WebDtos.PasswordRequest body,
                                               HttpServletRequest request) {
        char[] oldPassword = body.oldPassword().toCharArray();
        char[] newPassword = body.newPassword().toCharArray();
        try { services.auth().changePassword(WebSession.coreContext(request), oldPassword, newPassword); }
        finally {
            java.util.Arrays.fill(oldPassword, '\0');
            java.util.Arrays.fill(newPassword, '\0');
        }
        return ResponseEntity.noContent().build();
    }
}
