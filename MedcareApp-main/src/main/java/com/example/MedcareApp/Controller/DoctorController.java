package com.example.MedcareApp.Controller;


import com.example.MedcareApp.Entity.Doctor;
import com.example.MedcareApp.Interafce.DoctorRepository;
import com.example.MedcareApp.services.HrPayrollService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.example.MedcareApp.services.StaffIdentifierGenerator;
import com.example.MedcareApp.web.PayrollDoctorScheduleUpdateRequest;
import com.example.MedcareApp.web.PayrollDoctorEmployeeCodeUpdateRequest;
import com.example.MedcareApp.web.DoctorScheduleUpdateRequest;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private HrPayrollService hrPayrollService;

    @GetMapping
    public List<Doctor> getAllDoctors() {
        return doctorRepository.findAll().stream().map(this::ensureEmployeeId).toList();
    }

    @GetMapping("/employees")
    public List<Map<String, Object>> getDoctorEmployees() {
        return hrPayrollService.doctorEmployees();
    }

    @PutMapping("/employees/{employeeId}/schedule")
    public Doctor updateEmployeeDoctorSchedule(
            @PathVariable String employeeId, @RequestBody DoctorScheduleUpdateRequest request) {
        return hrPayrollService.updateDoctorSchedule(
                employeeId, request.availableTimes(), request.consultationFee());
    }

    @PutMapping("/payroll-employees/{employeeCode}/schedule")
    public Doctor updatePayrollDoctorSchedule(
            @PathVariable String employeeCode, @RequestBody PayrollDoctorScheduleUpdateRequest request) {
        return hrPayrollService.updatePayrollDoctorSchedule(employeeCode, request);
    }

    @PutMapping("/payroll-employees/{employeeCode}/identifier")
    public Doctor updatePayrollDoctorIdentifier(
            @PathVariable String employeeCode,
            @RequestBody PayrollDoctorEmployeeCodeUpdateRequest request) {
        return hrPayrollService.updatePayrollDoctorIdentifier(employeeCode, request.employeeCode());
    }

    @PostMapping
    public Doctor createDoctor(@RequestBody Doctor doctor) {
        if (doctor.getEmployeeId() == null || !doctor.getEmployeeId().startsWith("DT-")) {
            doctor.setEmployeeId(StaffIdentifierGenerator.generate("DT"));
        }
        return doctorRepository.save(doctor);
    }

    private Doctor ensureEmployeeId(Doctor doctor) {
        if (doctor.getEmployeeId() == null || !doctor.getEmployeeId().startsWith("DT-")) {
            doctor.setEmployeeId(StaffIdentifierGenerator.generate("DT"));
            return doctorRepository.save(doctor);
        }
        return doctor;
    }
}
