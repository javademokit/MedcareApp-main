package com.example.payroll.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "structures")
@Getter @Setter @NoArgsConstructor
public class Structure {
    @Id
    private Long id;
    private String name;
    /** Basic pay as a percentage of the employee's monthly gross. */
    private BigDecimal basicPct;
    private List<StructureComponent> components = new ArrayList<>();
}
