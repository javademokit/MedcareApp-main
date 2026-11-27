package com.pharmacy.model;

import java.util.Set;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document("users")
public class User {
    @Id private String id;
    @Indexed(unique = true) private String username;
    private String passwordHash;
    private String fullName;
    /** ADMIN, MANAGER, PHARMACIST, CASHIER, STOREKEEPER, AUDITOR */
    private Set<String> roles;
    private String pharmacistRegNo;
    private boolean active;
}
