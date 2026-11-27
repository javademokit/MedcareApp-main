package com.example.payroll.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PayrollSessionAccessService {
    private static final String ROLES_ATTRIBUTE = PayrollSessionAccessService.class.getName() + ".roles";
    private static final Set<String> PAYROLL_ROLES =
            Set.of("SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HR", "FINANCE");
    private static final Set<String> HR_ROLES =
            Set.of("SUPER_ADMIN", "HOSPITAL_ADMIN", "CLINIC_ADMIN", "HR");

    private final RestClient.Builder restClientBuilder;

    @Value("${medcare.auth-api-base-url:http://localhost:7771/api}")
    private String authApiBaseUrl;

    public Set<String> authorizePayroll(HttpServletRequest request) {
        Object existing = request.getAttribute(ROLES_ATTRIBUTE);
        if (existing instanceof Set<?> roles && roles.stream().allMatch(String.class::isInstance)) {
            return roles.stream().map(String.class::cast).collect(Collectors.toUnmodifiableSet());
        }

        String cookie = request.getHeader(HttpHeaders.COOKIE);
        if (cookie == null || cookie.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to access employee records");
        }

        Map<String, Object> account;
        try {
            account = restClientBuilder.build()
                    .get()
                    .uri(authApiBaseUrl.replaceAll("/+$", "") + "/users/me")
                    .header(HttpHeaders.COOKIE, cookie)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Your hospital session has expired");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not verify the hospital session");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not verify the hospital session");
        }

        Set<String> roles = readRoles(account);
        if (roles.stream().noneMatch(PAYROLL_ROLES::contains)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account cannot access payroll employee records");
        }
        request.setAttribute(ROLES_ATTRIBUTE, roles);
        return roles;
    }

    public void requireHrAccess(HttpServletRequest request) {
        if (authorizePayroll(request).stream().noneMatch(HR_ROLES::contains)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "HR or hospital administrator access is required");
        }
    }

    public boolean isWriteRequest(HttpServletRequest request) {
        return Set.of("POST", "PUT", "PATCH", "DELETE").contains(request.getMethod().toUpperCase(Locale.ROOT));
    }

    private Set<String> readRoles(Map<String, Object> account) {
        if (account == null || !(account.get("roles") instanceof List<?> roleValues)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The hospital session has no assigned roles");
        }
        Set<String> roles = new LinkedHashSet<>();
        for (Object roleValue : roleValues) {
            if (roleValue != null) {
                roles.add(roleValue.toString().trim().toUpperCase(Locale.ROOT).replaceFirst("^ROLE_", ""));
            }
        }
        return roles;
    }
}
