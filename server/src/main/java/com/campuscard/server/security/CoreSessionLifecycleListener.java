package com.campuscard.server.security;

import com.campuscard.api.ApplicationServices;
import com.campuscard.dto.SessionContext;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import com.campuscard.server.web.WebSession;

public final class CoreSessionLifecycleListener implements HttpSessionListener {
    private final ApplicationServices services;

    public CoreSessionLifecycleListener(ApplicationServices services) { this.services = services; }

    @Override public void sessionDestroyed(HttpSessionEvent event) {
        Object value = event.getSession().getAttribute(WebSession.CORE_CONTEXT_ATTRIBUTE);
        if (value instanceof SessionContext context) services.auth().logout(context);
    }
}
