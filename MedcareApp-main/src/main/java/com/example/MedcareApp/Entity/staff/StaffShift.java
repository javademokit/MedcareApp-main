package com.example.MedcareApp.Entity.staff;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Document(collection = "staff_shifts")
public class StaffShift {
    @Id
    private String id;
    @NotBlank
    private String staffId;
    @NotBlank
    private String staffName;
    @NotBlank
    private String staffRole;
    @NotBlank
    private String department;
    @NotBlank
    private String shiftDate;
    @NotBlank
    private String startTime;
    @NotBlank
    private String endTime;
    private String status = "SCHEDULED";
    private String notes;
    private Instant createdAt;
    private Instant checkInAt;
    private Instant checkOutAt;
}
