package com.example.payroll.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Frozen payslip (JSON) saved when a month is processed, so later salary or template edits never rewrite history. */
@Document(collection = "payslips")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PayslipSnapshot {
    @Id
    private Long id;
    private Long runId;
    private Long employeeId;
    private String payload;
}
