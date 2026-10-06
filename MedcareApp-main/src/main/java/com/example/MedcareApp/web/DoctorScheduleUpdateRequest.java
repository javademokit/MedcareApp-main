package com.example.MedcareApp.web;

import java.util.List;

public record DoctorScheduleUpdateRequest(List<String> availableTimes, Double consultationFee) {
}
