package com.example.MedcareApp.web;

public record PrescribableMedication(
        String id,
        String name,
        String department,
        String strength,
        String dosageForm,
        int quantityAvailable) {
}
