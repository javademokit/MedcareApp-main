package com.example.MedcareApp.web;

import java.util.List;

public record DashboardSummary(
        int activeAdmissions,
        int openEmergencies,
        int resultsAwaitingReview,
        int testsOrdered,
        int testsInProgress,
        int totalBeds,
        int availableBeds,
        int occupiedBeds,
        int totalNurses,
        List<ActivityPoint> dailyActivity,
        List<ActivityPoint> hourlyActivity,
        List<WardCount> wardData) {
    public record ActivityPoint(
            String key,
            String label,
            int hour,
            int visits,
            int emergency,
            int diagnostics,
            int admissions) {}

    public record WardCount(String unit, int patients, int totalBeds, int availableBeds, int occupiedBeds) {}
}
