package com.example.payroll.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "payrollRuns")
@Getter @Setter @NoArgsConstructor
public class PayrollRun {
    @Id
    private Long id;
    @Indexed(unique = true)
    private String payMonth;
    private RunMode runMode;
    private LocalDateTime runAt;
}
