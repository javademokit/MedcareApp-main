package com.example.MedcareApp.web;

import java.util.List;

public record PayrollDoctorScheduleUpdateRequest(
        String doctorName,
        String doctorSpecialistName,
        String doctorMobileNo,
        String doctorDestination,
        List<String> availableTimes,
        Double consultationFee) {
}
