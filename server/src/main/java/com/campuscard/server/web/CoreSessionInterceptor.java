package com.campuscard.server.web;

import com.campuscard.api.ApplicationServices;
import com.campuscard.dto.SessionContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class CoreSessionInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    private final ApplicationServices services;
    public CoreSessionInterceptor(ApplicationServices services) { this.services = services; }

    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/csrf", "/api/auth/login", "/api/auth/logout", "/api/health");
    }

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        SessionContext context = WebSession.coreContext(request);
        services.auth().currentUser(context);
        return true;
    }
}
