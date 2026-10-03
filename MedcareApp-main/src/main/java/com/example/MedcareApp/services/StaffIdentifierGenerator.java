package com.example.MedcareApp.services;

import java.util.UUID;

public final class StaffIdentifierGenerator {
    private StaffIdentifierGenerator() {}

    public static String generate(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase();
    }
}
