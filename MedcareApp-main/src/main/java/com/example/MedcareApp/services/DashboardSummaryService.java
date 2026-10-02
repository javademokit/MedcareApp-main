package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.Appointment;
import com.example.MedcareApp.Entity.emergency.EmergencyCase;
import com.example.MedcareApp.testModel.MedicalTest;
import com.example.MedcareApp.web.DashboardSummary;
import com.example.MedcareApp.web.DashboardSummary.ActivityPoint;
import com.example.MedcareApp.web.DashboardSummary.WardCount;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class DashboardSummaryService {
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final Pattern HOUR_PATTERN = Pattern.compile(
            "^\\s*(\\d{1,2})(?::\\d{2})?\\s*(AM|PM)?", Pattern.CASE_INSENSITIVE);
    private final DashboardSummaryDataSource dataSource;

    public DashboardSummaryService(DashboardSummaryDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public DashboardSummary getSummary() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate firstDay = today.minusDays(29);
        Map<LocalDate, int[]> daily = new LinkedHashMap<>();
        for (int offset = 0; offset < 30; offset++) {
            daily.put(firstDay.plusDays(offset), new int[4]);
        }
        int[] hourlyAppointments = new int[24];
        int[] hourlyEmergencies = new int[24];

        List<Appointment> appointments = dataSource.findAppointments(firstDay, today);
        for (Appointment appointment : appointments) {
            if ("cancelled".equalsIgnoreCase(appointment.getAppointmentStatus())) continue;
            LocalDate date = parseDate(appointment.getDate());
            int[] counts = daily.get(date);
            if (counts != null) counts[0]++;
            if (today.equals(date)) {
                Integer hour = parseHour(appointment.getTime());
                if (hour != null) hourlyAppointments[hour]++;
            }
        }

        Instant firstInstant = firstDay.atStartOfDay(ZONE).toInstant();
        Instant nextDayInstant = today.plusDays(1).atStartOfDay(ZONE).toInstant();
        int openEmergencies = dataSource.countOpenEmergencies();
        List<EmergencyCase> emergencies = dataSource.findEmergencies(firstInstant, nextDayInstant);
        for (EmergencyCase emergency : emergencies) {
            Instant createdAt = emergency.getCreatedAt();
            if (createdAt == null) continue;
            LocalDate date = createdAt.atZone(ZONE).toLocalDate();
            int[] counts = daily.get(date);
            if (counts != null) counts[1]++;
            if (today.equals(date)) hourlyEmergencies[createdAt.atZone(ZONE).getHour()]++;
        }

        List<MedicalTest> tests = dataSource.findTests(firstDay, today);
        int awaitingReview = dataSource.countTestsWithStatus("RESULT_READY");
        int ordered = dataSource.countTestsWithStatus("ORDERED");
        int inProgress = dataSource.countTestsWithStatus("IN_PROGRESS");
        for (MedicalTest test : tests) {
            int[] counts = daily.get(parseDate(test.getOrderDate()));
            if (counts != null) counts[2]++;
        }

        List<PatientAdmissionBucket> admissionBuckets = dataSource.findActivePatientAdmissionBuckets();
        int activeAdmissions = 0;
        Map<String, Integer> wardCounts = new LinkedHashMap<>();
        for (PatientAdmissionBucket bucket : admissionBuckets) {
            activeAdmissions += bucket.count();
            String ward = bucket.ward() == null || bucket.ward().isBlank()
                    ? "Ward not assigned" : bucket.ward();
            wardCounts.merge(ward, bucket.count(), Integer::sum);
            int[] counts = daily.get(parseDate(bucket.admitDate()));
            if (counts != null) counts[3] += bucket.count();
        }

        List<ActivityPoint> dailyPoints = new ArrayList<>(daily.size());
        daily.forEach((date, counts) -> dailyPoints.add(new ActivityPoint(
                date.toString(), date.toString(), -1, counts[0], counts[1], counts[2], counts[3])));
        List<ActivityPoint> hourlyPoints = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            hourlyPoints.add(new ActivityPoint(today.toString(), String.format("%02d:00", hour),
                    hour, hourlyAppointments[hour], hourlyEmergencies[hour], 0, 0));
        }
        List<WardCount> wardData = wardCounts.entrySet().stream()
                .map(entry -> new WardCount(entry.getKey(), entry.getValue()))
                .toList();
        return new DashboardSummary(activeAdmissions, openEmergencies, awaitingReview,
                ordered, inProgress, dailyPoints, hourlyPoints, wardData);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.substring(0, Math.min(value.length(), 10)));
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private Integer parseHour(String value) {
        if (value == null) return null;
        var matcher = HOUR_PATTERN.matcher(value);
        if (!matcher.find()) return null;
        int hour = Integer.parseInt(matcher.group(1));
        if (hour > 23) return null;
        String meridiem = matcher.group(2);
        if (meridiem != null) {
            if (meridiem.toUpperCase(Locale.ROOT).equals("PM") && hour < 12) hour += 12;
            if (meridiem.toUpperCase(Locale.ROOT).equals("AM") && hour == 12) hour = 0;
        }
        return hour;
    }
}
