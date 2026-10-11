package com.campuscard.server.security;

import com.campuscard.server.web.WebDtos.ErrorJson;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
public final class ApiSecurityErrors implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper mapper;

    public ApiSecurityErrors(ObjectMapper mapper) { this.mapper = mapper; }

    @Override public void commence(HttpServletRequest request, HttpServletResponse response,
                                   AuthenticationException exception) throws IOException, ServletException {
        write(response, 401, "AUTH_REQUIRED", "请先登录");
    }

    @Override public void handle(HttpServletRequest request, HttpServletResponse response,
                                 AccessDeniedException exception) throws IOException, ServletException {
        if (exception instanceof CsrfException) write(response, 403, "CSRF_INVALID", "请求令牌无效，请刷新页面后重试");
        else write(response, 403, "FORBIDDEN", "无权执行此操作");
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), new ErrorJson(code, message));
    }
}
