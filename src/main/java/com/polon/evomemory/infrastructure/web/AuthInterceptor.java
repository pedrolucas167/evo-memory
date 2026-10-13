package com.polon.evomemory.infrastructure.web;
import com.polon.evomemory.application.auth.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import java.util.Map;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private final ApiRateLimit rateLimit;

    public AuthInterceptor(AuthService authService, ApiRateLimit rateLimit) {
        this.authService = authService;
        this.rateLimit = rateLimit;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String auth = request.getHeader("Authorization");
        String userFromToken = authService.authenticate(auth);
        if (userFromToken == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("WWW-Authenticate", "Bearer");
            return false;
        }
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables instanceof Map<?, ?> paths && paths.containsKey("userId")
                && !userFromToken.equals(paths.get("userId"))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        request.setAttribute("authUserId", userFromToken);
        if (!rateLimit.allow(userFromToken)) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            return false;
        }
        return true;
    }
}
