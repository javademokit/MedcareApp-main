package com.example.payroll.service;

import com.example.payroll.dto.Dtos.EmployeeDto;
import com.example.payroll.model.Employee;
import com.example.payroll.model.Structure;
import com.example.payroll.repo.EmployeeRepository;
import com.example.payroll.repo.StructureRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeServiceTest {
    @Test
    void generatesSixDigitCodesByRole() {
        assertEquals("DT-000001", EmployeeService.employeeCodeForRole("Doctor", 1));
        assertEquals("DT-000012", EmployeeService.employeeCodeForRole("MD / Consultant", 12));
        assertEquals("NR-000002", EmployeeService.employeeCodeForRole("Nurse", 2));
        assertEquals("EMP-000003", EmployeeService.employeeCodeForRole("Lab Engineer", 3));
    }

    @Test
    void rejectsNumbersThatDoNotFitTheSixDigitFormat() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> EmployeeService.employeeCodeForRole("Doctor", 1_000_000));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void acceptsOnlyRoleMatchingSixDigitIdsForCodeUpdates() {
        assertTrue(EmployeeService.employeeCodeMatchesRole("DT-000001", "Doctor"));
        assertTrue(EmployeeService.employeeCodeMatchesRole("NR-000001", "Nurse"));
        assertTrue(EmployeeService.employeeCodeMatchesRole("EMP-000001", "IT Engineer"));
        assertFalse(EmployeeService.employeeCodeMatchesRole("E111", "Doctor"));
        assertFalse(EmployeeService.employeeCodeMatchesRole("NR-000001", "Doctor"));
    }

    @Test
    void savesBankDetailsAndNormalizesIfscCode() {
        EmployeeRepository employees = mock(EmployeeRepository.class);
        StructureRepository structures = mock(StructureRepository.class);
        Structure structure = new Structure();
        structure.setId(4L);
        structure.setName("Hospital Standard");
        when(employees.findAllByOrderByIdAsc()).thenReturn(List.of());
        when(structures.findById(4L)).thenReturn(Optional.of(structure));
        when(employees.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            employee.setId(11L);
            return employee;
        });
        EmployeeDto request = new EmployeeDto(
                null, null, "Asha Rao", "Pediatrics", "Doctor", new BigDecimal("80000"), null,
                "123456789012", "hdfc0001234", "City Branch", "Bengaluru",
                4L, null, null, null, null, null, null, null, null, null, null,
                false, false, false, false, false);

        EmployeeDto saved = new EmployeeService(employees, structures).save(null, request);

        assertEquals("123456789012", saved.bankAccountNumber());
        assertEquals("HDFC0001234", saved.ifscCode());
        assertEquals("City Branch", saved.bankBranchName());
        assertEquals("Bengaluru", saved.bankCity());
        verify(employees).save(any(Employee.class));
    }

    @Test
    void rejectsMalformedIfscCodes() {
        EmployeeRepository employees = mock(EmployeeRepository.class);
        StructureRepository structures = mock(StructureRepository.class);
        EmployeeDto request = new EmployeeDto(
                null, null, "Asha Rao", "Pediatrics", "Doctor", new BigDecimal("80000"), null,
                null, "BAD", null, null, 4L, null, null, null, null, null, null, null, null, null, null,
                false, false, false, false, false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> new EmployeeService(employees, structures).save(null, request));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }
}
