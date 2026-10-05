package com.example.MedcareApp.Entity.hrpayroll;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "hr_leave_requests")
public class LeaveRequest {
    @Id
    private String id = UUID.randomUUID().toString();
    private String employeeId;
    private String leaveType;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String reason;
    private String status = "PENDING";
    private String approvedBy;
    private Instant approvedAt;
    private Instant createdAt = Instant.now();
}
