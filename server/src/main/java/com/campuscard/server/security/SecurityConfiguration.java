package com.campuscard.server.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import com.campuscard.api.ApplicationServices;

@Configuration
public class SecurityConfiguration {
    @Bean
    public ServletListenerRegistrationBean<CoreSessionLifecycleListener> coreSessionLifecycleListener(
            ApplicationServices services) {
        return new ServletListenerRegistrationBean<>(new CoreSessionLifecycleListener(services));
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setSessionAttributeName("_campus_csrf");
        return repository;
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ApiSecurityErrors errors,
                                                   CsrfTokenRepository csrfTokens,
                                                   SecurityContextRepository securityContexts) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokens)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .securityContext(context -> context.securityContextRepository(securityContexts))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/csrf", "/api/auth/login", "/api/health",
                                "/", "/login", "/dashboard", "/wallet", "/transactions", "/users",
                                "/statistics", "/settings", "/assets/**", "/favicon.ico").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll());
        return http.build();
    }
}
