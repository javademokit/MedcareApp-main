package com.example.MedcareApp.Entity.payroll;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "payroll_nurse_attendance")
public class NurseAttendanceSummary {
    @Id
    private String id;
    private String nurseId;
    private LocalDate fromDate;
    private LocalDate toDate;
    private int presentDays;
    private int absentDays;
    private int lopDays;
    private int nightShifts;
    private int eveningShifts;
    private int holidayDutyDays;
    private int weeklyOffDutyDays;
    private int lateMarks;
    private Map<String, Integer> wardDays = new HashMap<>();
    private boolean finalized;
    private String finalizedBy;
    private Instant updatedAt = Instant.now();
}
