package com.pharmacy.service;

import com.pharmacy.model.AuditLog;
import com.pharmacy.repo.Repos.AuditRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditRepository repo;

    public static String currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? "system" : a.getName();
    }

    public void log(String action, String entity, String entityId, String details) {
        repo.save(AuditLog.builder().at(LocalDateTime.now()).user(currentUser())
                .action(action).entity(entity).entityId(entityId).details(details).build());
    }
}
