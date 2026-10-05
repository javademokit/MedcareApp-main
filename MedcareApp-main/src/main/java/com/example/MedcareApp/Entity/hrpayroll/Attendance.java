package com.example.MedcareApp.Entity.hrpayroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "hr_attendance")
public class Attendance {
    @Id
    private String id = UUID.randomUUID().toString();
    private String employeeId;
    private LocalDate attendanceDate;
    private String checkIn;
    private String checkOut;
    private String status;
    private BigDecimal workedHours = BigDecimal.ZERO;
    private BigDecimal overtimeHours = BigDecimal.ZERO;
    private Instant createdAt = Instant.now();
}
