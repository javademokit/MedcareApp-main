package com.example.MedcareApp.services;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public final class PatientIdentifierGenerator {
    private PatientIdentifierGenerator() {}

    public static String generate(Predicate<String> alreadyExists) {
        for (int attempt = 0; attempt < 1000; attempt++) {
            String patientId = "PT-" + ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
            if (!alreadyExists.test(patientId)) return patientId;
        }
        throw new IllegalStateException("Could not generate a unique patient ID");
    }
}
