package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.example.MedcareApp.Entity.hrpayroll.Attendance;
import com.example.MedcareApp.Entity.Doctor;
import com.example.MedcareApp.Entity.hrpayroll.Employee;
import com.example.MedcareApp.Entity.hrpayroll.EmployeeType;
import com.example.MedcareApp.Entity.hrpayroll.LeaveRequest;
import com.example.MedcareApp.Entity.hrpayroll.OvertimeAllowanceRequest;
import com.example.MedcareApp.Entity.hrpayroll.PayrollRun;
import com.example.MedcareApp.Entity.hrpayroll.Payslip;
import com.example.MedcareApp.Entity.hrpayroll.SalaryComponent;
import com.example.MedcareApp.Entity.hrpayroll.SalaryStructure;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.server.ResponseStatusException;

class HrPayrollServiceTest {
    @Test
    void explainsWhichSavedPayrollRunBlocksCreatingADuplicate() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        PayrollRun existingRun = new PayrollRun();
        existingRun.setId("payroll-oct-2026");
        existingRun.setMonth("2026-10");
        existingRun.setStatus("DRAFT");
        when(mongo.findOne(any(Query.class), eq(PayrollRun.class))).thenReturn(existingRun);

        ResponseStatusException error = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> new HrPayrollService(mongo).createPayrollRun("2026-10", "hr@example.test"));

        assertEquals(409, error.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason().contains("payroll-oct-2026"));
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason().contains("status: DRAFT"));
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason().contains("Payroll History"));
        verify(mongo, never()).save(any(PayrollRun.class));
    }

    @Test
    void normalizesOptionalStatutoryIdentifiersAndDoesNotExposeFullAadhaarInEmployeeList() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        EmployeeType otherType = new EmployeeType();
        otherType.setCode("OTHER");
        otherType.setName("Other");
        otherType.setStatus("ACTIVE");
        when(mongo.findAll(EmployeeType.class)).thenReturn(List.of(otherType));
        when(mongo.findOne(any(Query.class), eq(Employee.class))).thenReturn(null);
        when(mongo.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Employee employee = new Employee();
        employee.setFirstName("Riya");
        employee.setLastName("Shah");
        employee.setMobile("5551234567");
        employee.setEmployeeType("OTHER");
        employee.setJoiningDate(LocalDate.of(2026, 10, 1));
        employee.setPanNumber("abcde1234f");
        employee.setAadhaarLastFour("9012");
        employee.setPfUanNumber(" 123456789012 ");

        HrPayrollService service = new HrPayrollService(mongo);
        Employee saved = service.saveEmployee(employee);
        when(mongo.findAll(Employee.class)).thenReturn(List.of(saved));

        Map<String, Object> row = service.employees().get(0);

        assertEquals("ABCDE1234F", saved.getPanNumber());
        assertEquals("9012", saved.getAadhaarLastFour());
        assertEquals("123456789012", saved.getPfUanNumber());
        org.junit.jupiter.api.Assertions.assertFalse(row.containsKey("aadhaarNumber"));
        assertEquals("9012", row.get("aadhaarLastFour"));
    }

    @Test
    void generatesNurseSpecificEmployeeIdForNurseEmployment() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        EmployeeType nurseType = new EmployeeType();
        nurseType.setCode("NURSE");
        nurseType.setName("Nurse");
        nurseType.setStatus("ACTIVE");
        when(mongo.findAll(EmployeeType.class)).thenReturn(List.of(nurseType));
        when(mongo.findOne(any(Query.class), eq(Employee.class))).thenReturn(null);
        when(mongo.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Employee employee = new Employee();
        employee.setFirstName("Anita");
        employee.setLastName("Sharma");
        employee.setMobile("5551234567");
        employee.setEmail("anita@example.test");
        employee.setEmployeeType("NURSE");
        employee.setJoiningDate(LocalDate.of(2026, 10, 1));

        Employee created = new HrPayrollService(mongo).saveEmployee(employee);

        assertEquals("NUR-", created.getEmployeeCode().substring(0, 4));
    }

    @Test
    void includesCompanyBrandingAndMaskedIdentityDetailsInPayslipPdf() {
        Payslip payslip = new Payslip();
        payslip.setEmployeeName("Riya Shah");
        payslip.setEmployeeCode("EMP-100");
        payslip.setPanNumber("ABCDE1234F");
        payslip.setAadhaarLastFour("9012");
        payslip.setPfUanNumber("123456789012");
        payslip.setMonth("2026-10");

        String pdf = new String(new HrPayrollService(mock(MongoTemplate.class)).payslipPdf(payslip),
                StandardCharsets.US_ASCII);

        org.junit.jupiter.api.Assertions.assertTrue(pdf.contains("(MEDCARE) Tj"));
        org.junit.jupiter.api.Assertions.assertTrue(pdf.contains("PAN: ABCDE1234F"));
        org.junit.jupiter.api.Assertions.assertTrue(pdf.contains("Aadhaar: XXXX XXXX 9012"));
        org.junit.jupiter.api.Assertions.assertTrue(pdf.contains("PF / UAN: 123456789012"));
        org.junit.jupiter.api.Assertions.assertFalse(pdf.contains("1234 5678 9012"));
    }

    @Test
    void addsOnlyTheIndividuallyApprovedOvertimeAmountToEmployeeGrossAndNet() {
        PayrollRun.PayrollItem item = new PayrollRun.PayrollItem();
        item.setGrossSalary(new BigDecimal("50000.00"));
        item.setTotalDeduction(new BigDecimal("5000.00"));
        item.setNetSalary(new BigDecimal("45000.00"));

        HrPayrollService.applyOvertimeAllowance(item, new BigDecimal("2750.50"));

        assertEquals(new BigDecimal("52750.50"), item.getGrossSalary());
        assertEquals(new BigDecimal("5000.00"), item.getTotalDeduction());
        assertEquals(new BigDecimal("47750.50"), item.getNetSalary());
        assertEquals("OVERTIME_ALLOWANCE", item.getEarnings().get(0).getCode());
        assertEquals(new BigDecimal("2750.50"), item.getEarnings().get(0).getAmount());
    }

    @Test
    void submitsOvertimeRequestAsPendingAndApprovesHrEnteredAmount() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        Employee employee = new Employee();
        employee.setId("employee-ot");
        employee.setEmployeeCode("EMP-OT");
        employee.setFirstName("Riya");
        employee.setLastName("Shah");
        employee.setEmail("riya@example.test");
        employee.setStatus("ACTIVE");
        when(mongo.findOne(any(Query.class), eq(Employee.class))).thenReturn(employee);
        when(mongo.findOne(any(Query.class), eq(PayrollRun.class))).thenReturn(null);
        when(mongo.save(any(OvertimeAllowanceRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HrPayrollService service = new HrPayrollService(mongo);
        OvertimeAllowanceRequest submitted = service.requestOvertimeAllowance(
                LocalDate.of(2026, 10, 4), new BigDecimal("2.5"), "Emergency ward coverage", "riya@example.test");

        assertEquals("PENDING", submitted.getStatus());
        assertEquals("2026-10", submitted.getMonth());
        assertEquals(new BigDecimal("2.5"), submitted.getHours());
        assertEquals(null, submitted.getApprovedAmount());
        when(mongo.findById(submitted.getId(), OvertimeAllowanceRequest.class)).thenReturn(submitted);

        OvertimeAllowanceRequest approved = service.decideOvertimeAllowance(
                submitted.getId(), "APPROVE", new BigDecimal("1250.75"), "hr@example.test");

        assertEquals("APPROVED", approved.getStatus());
        assertEquals(new BigDecimal("1250.75"), approved.getApprovedAmount());
        assertEquals("hr@example.test", approved.getApprovedBy());
    }

    @Test
    void createsAndLinksDoctorScheduleProfileWhenHrCreatesDoctorEmployee() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        EmployeeType doctorType = new EmployeeType();
        doctorType.setId("doctor-type");
        doctorType.setCode("DOCTOR");
        doctorType.setName("Doctor");
        doctorType.setStatus("ACTIVE");
        when(mongo.findAll(EmployeeType.class)).thenReturn(List.of(doctorType));
        when(mongo.findOne(any(Query.class), eq(Employee.class))).thenReturn(null);
        when(mongo.save(any(Doctor.class))).thenAnswer(invocation -> {
            Doctor doctor = invocation.getArgument(0);
            doctor.setId("doctor-profile-1");
            return doctor;
        });
        when(mongo.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Employee employee = new Employee();
        employee.setFirstName("Mira");
        employee.setLastName("Patel");
        employee.setMobile("5551234567");
        employee.setEmployeeType("DOCTOR");
        employee.setJoiningDate(LocalDate.of(2026, 10, 1));
        employee.setDepartmentName("Cardiology");
        employee.setProfessionalInfo(java.util.Map.of("specialization", "Cardiology"));
        employee.setDoctorConsultationFee(650.0);
        employee.setDoctorAvailableTimes(List.of("09:00", "09:30", "09:00"));

        Employee created = new HrPayrollService(mongo).saveEmployee(employee);

        assertEquals("doctor-profile-1", created.getDoctorProfileId());
        assertEquals("DT-", created.getEmployeeCode().substring(0, 3));
        org.mockito.ArgumentCaptor<Doctor> doctorCaptor = org.mockito.ArgumentCaptor.forClass(Doctor.class);
        verify(mongo).save(doctorCaptor.capture());
        Doctor doctor = doctorCaptor.getValue();
        assertEquals(created.getEmployeeCode(), doctor.getEmployeeId());
        assertEquals("Mira Patel", doctor.getDoctorName());
        assertEquals("Cardiology", doctor.getDoctorSpecialistName());
        assertEquals("Cardiology", doctor.getDoctorDestination());
        assertEquals(List.of("09:00", "09:30"), doctor.getDoctorAvailabletime());
        assertEquals(650.0, doctor.getDoctorfee());
    }

    @Test
    void listsDoctorEmployeesWithoutExposingSensitiveEmployeeFields() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        Employee employee = new Employee();
        employee.setId("employee-1");
        employee.setEmployeeCode("DT-1234ABCD");
        employee.setFirstName("Mira");
        employee.setLastName("Patel");
        employee.setEmployeeType("DOCTOR");
        employee.setStatus("ACTIVE");
        employee.setProfessionalInfo(Map.of("specialization", "Cardiology"));
        employee.setPanNumber("ABCDE1234F");
        employee.setAadhaarLastFour("9012");
        when(mongo.findAll(Employee.class)).thenReturn(List.of(employee));

        List<Map<String, Object>> doctors = new HrPayrollService(mongo).doctorEmployees();

        assertEquals(1, doctors.size());
        assertEquals("DT-1234ABCD", doctors.get(0).get("employeeCode"));
        assertEquals("Mira Patel", doctors.get(0).get("doctorName"));
        assertEquals("Cardiology", doctors.get(0).get("doctorSpecialistName"));
        org.junit.jupiter.api.Assertions.assertFalse(doctors.get(0).containsKey("panNumber"));
        org.junit.jupiter.api.Assertions.assertFalse(doctors.get(0).containsKey("aadhaarLastFour"));
    }

    @Test
    void updatesEmployeeDoctorScheduleAndKeepsLinkedDoctorProfile() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        Employee employee = new Employee();
        employee.setId("employee-1");
        employee.setEmployeeCode("DT-1234ABCD");
        employee.setFirstName("Mira");
        employee.setLastName("Patel");
        employee.setEmployeeType("DOCTOR");
        employee.setStatus("ACTIVE");
        employee.setProfessionalInfo(Map.of("specialization", "Cardiology"));
        Doctor profile = new Doctor();
        profile.setId("doctor-profile-1");
        when(mongo.findById("employee-1", Employee.class)).thenReturn(employee);
        when(mongo.findOne(any(Query.class), eq(Doctor.class))).thenReturn(profile);
        when(mongo.findById("doctor-profile-1", Doctor.class)).thenReturn(profile);
        when(mongo.save(any(Doctor.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mongo.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Doctor updated = new HrPayrollService(mongo).updateDoctorSchedule(
                "employee-1", List.of("09:30", "09:00", "09:30"), 750.0);

        assertEquals("doctor-profile-1", updated.getId());
        assertEquals("DT-1234ABCD", updated.getEmployeeId());
        assertEquals("Mira Patel", updated.getDoctorName());
        assertEquals(List.of("09:00", "09:30"), updated.getDoctorAvailabletime());
        assertEquals(List.of("09:00", "09:30"), employee.getDoctorAvailableTimes());
        assertEquals(750.0, updated.getDoctorfee());
    }

    @Test
    void refusesToCalculatePayrollWhenThereAreNoEligibleEmployees() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        PayrollRun run = new PayrollRun();
        run.setId("run-1");
        run.setMonth("2026-10");
        run.setStatus("DRAFT");
        when(mongo.findById("run-1", PayrollRun.class)).thenReturn(run);
        when(mongo.find(any(Query.class), eq(Employee.class))).thenReturn(List.of());

        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> new HrPayrollService(mongo).calculatePayroll("run-1"));

        assertEquals(422, exception.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertTrue(exception.getReason().contains(
                "Add and activate employees before calculating payroll."));
        verify(mongo, never()).save(run);
    }

    @Test
    void calculatesAndCombinesPayAcrossMidMonthSalaryChange() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        SalaryComponent basic = new SalaryComponent();
        basic.setCode("BASIC");
        basic.setName("Basic salary");
        basic.setType("EARNING");
        basic.setCalculationType("FIXED");
        when(mongo.findById(basic.getId(), SalaryComponent.class)).thenReturn(basic);
        when(mongo.find(any(Query.class), eq(Attendance.class))).thenReturn(List.of());
        when(mongo.find(any(Query.class), eq(LeaveRequest.class))).thenReturn(List.of());

        Employee employee = new Employee();
        employee.setId("employee-1");
        employee.setEmployeeCode("EMP-1");
        employee.setFirstName("Riya");
        employee.setLastName("Shah");
        employee.setJoiningDate(LocalDate.of(2025, 1, 1));

        SalaryStructure previous = structure(basic, new BigDecimal("31000"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 15));
        SalaryStructure updated = structure(basic, new BigDecimal("62000"),
                LocalDate.of(2026, 10, 16), null);
        List<SalaryStructureTimeline.Segment> segments = SalaryStructureTimeline.segments(
                employee, List.of(previous, updated), YearMonth.of(2026, 10));

        PayrollRun.PayrollItem item = new HrPayrollService(mongo)
                .calculateEmployee(employee, segments, YearMonth.of(2026, 10));

        assertEquals(new BigDecimal("47000.00"), item.getGrossSalary());
        assertEquals(1, item.getEarnings().size());
        assertEquals(new BigDecimal("47000.00"), item.getEarnings().get(0).getAmount());
        assertEquals(31, item.getWorkingDays());
        assertEquals(new BigDecimal("31.00"), item.getPaidDays());
    }

    private SalaryStructure structure(
            SalaryComponent component, BigDecimal amount, LocalDate from, LocalDate to) {
        SalaryStructure structure = new SalaryStructure();
        structure.setEffectiveFrom(from);
        structure.setEffectiveTo(to);
        SalaryStructure.ComponentLine line = new SalaryStructure.ComponentLine();
        line.setComponentId(component.getId());
        line.setAmount(amount);
        structure.setComponents(List.of(line));
        return structure;
    }
}
