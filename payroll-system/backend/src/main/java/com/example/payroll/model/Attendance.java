package com.example.payroll.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDate;

/** Only exceptions are stored. A weekday with no row counts as PRESENT; Saturdays and Sundays are weekly offs. */
@Document(collection = "attendance")
@CompoundIndex(name = "employee_date", def = "{'employeeId': 1, 'workDate': 1}", unique = true)
@Getter @Setter @NoArgsConstructor
public class Attendance {
    @Id
    private Long id;
    private Long employeeId;
    private LocalDate workDate;
    private AttendanceStatus status;
}
