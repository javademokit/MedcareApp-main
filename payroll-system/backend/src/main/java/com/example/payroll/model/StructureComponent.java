package com.example.payroll.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

/** Embedded on a salary structure. Order in the list is the display order. */
@Getter @Setter @NoArgsConstructor
public class StructureComponent {
    private String name;
    private ComponentKind kind;
    private CalcType calcType;
    private BigDecimal calcValue = BigDecimal.ZERO;
    /** Optional ceiling on the basic used for a PCT_BASIC deduction (e.g. PF wage ceiling). */
    private BigDecimal capAmount;
}
