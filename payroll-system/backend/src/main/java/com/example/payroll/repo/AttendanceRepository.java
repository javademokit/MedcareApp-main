package com.example.payroll.repo;

import com.example.payroll.model.Attendance;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends MongoRepository<Attendance, Long> {
    List<Attendance> findByWorkDateBetween(LocalDate from, LocalDate to);
    Optional<Attendance> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate date);
}
