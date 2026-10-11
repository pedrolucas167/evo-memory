package com.polon.evomemory.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String auth = request.getHeader("Authorization");
        String userFromToken = authService.authenticate(auth);
        if (userFromToken != null) {
            request.setAttribute("authUserId", userFromToken);
        }
        return true;
    }
}
