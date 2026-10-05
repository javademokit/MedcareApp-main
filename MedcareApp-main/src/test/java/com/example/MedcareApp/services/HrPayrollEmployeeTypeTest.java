package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Entity.hrpayroll.EmployeeType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

class HrPayrollEmployeeTypeTest {
    @Test
    void seedsMissingDefaultTypesWhenCustomTypesAlreadyExist() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        List<EmployeeType> storedTypes = new ArrayList<>();
        EmployeeType custom = new EmployeeType();
        custom.setName("Volunteer");
        custom.setCode("VOLUNTEER");
        storedTypes.add(custom);

        when(mongo.findAll(EmployeeType.class)).thenAnswer(invocation -> new ArrayList<>(storedTypes));
        when(mongo.save(any(EmployeeType.class))).thenAnswer(invocation -> {
            EmployeeType saved = invocation.getArgument(0);
            storedTypes.add(saved);
            return saved;
        });

        List<EmployeeType> employeeTypes = new HrPayrollService(mongo).employeeTypes();

        assertTrue(employeeTypes.stream().anyMatch(type -> "OTHER".equals(type.getCode())
                && "ACTIVE".equals(type.getStatus())));
        assertTrue(employeeTypes.stream().anyMatch(type -> "DOCTOR".equals(type.getCode())));
        assertTrue(employeeTypes.stream().anyMatch(type -> "VOLUNTEER".equals(type.getCode())));
    }
}
