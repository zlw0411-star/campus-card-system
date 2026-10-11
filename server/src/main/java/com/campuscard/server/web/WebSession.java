package com.campuscard.server.web;

import com.campuscard.dto.SessionContext;
import com.campuscard.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public final class WebSession {
    public static final String CORE_CONTEXT_ATTRIBUTE = WebSession.class.getName() + ".coreSession";
    private WebSession() { }

    public static SessionContext coreContext(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(CORE_CONTEXT_ATTRIBUTE);
        if (value instanceof SessionContext context) return context;
        throw new BusinessException("AUTH_REQUIRED", "请先登录");
    }
}
