package com.example.payroll.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class EmployeeAccessInterceptor implements HandlerInterceptor {
    private final PayrollSessionAccessService sessionAccess;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        sessionAccess.authorizePayroll(request);
        String path = request.getRequestURI();
        if (path.contains("/onboard") || path.contains("/documents") || path.contains("/profile-picture")) {
            sessionAccess.requireHrAccess(request);
        }
        if (sessionAccess.isWriteRequest(request)) {
            requireCsrf(request);
        }
        return true;
    }

    private void requireCsrf(HttpServletRequest request) {
        String header = request.getHeader("X-XSRF-TOKEN");
        String cookie = request.getCookies() == null ? null : Arrays.stream(request.getCookies())
                .filter(value -> "XSRF-TOKEN".equals(value.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
        if (header == null || cookie == null
                || !cookie.equals(header)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Request security token is missing or invalid");
        }
    }
}
