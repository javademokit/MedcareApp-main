package com.example.payroll.service;

import com.example.payroll.dto.Dtos.*;
import com.example.payroll.model.*;
import com.example.payroll.repo.EmployeeRepository;
import com.example.payroll.repo.StructureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StructureService {
    private final StructureRepository repo;
    private final EmployeeRepository employees;
    private final PayrollCalculator calculator;

    public List<StructureDto> list() {
        return repo.findAll(Sort.by("id")).stream().map(this::toDto).toList();
    }

    public StructureDto save(Long id, StructureDto dto) {
        Structure s = id == null ? new Structure()
                : repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Structure not found"));
        apply(s, dto);
        return toDto(repo.save(s));
    }

    public void delete(Long id) {
        boolean assigned = employees.findAll().stream()
                .anyMatch(e -> e.getStructure() != null && id.equals(e.getStructure().getId()));
        if (assigned) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Structure is assigned to employees. Reassign them first.");
        }
        repo.deleteById(id);
    }

    /** Full-month breakdown (everyone present) for a draft structure, using the same calculator as real payroll. */
    public PayrollRow preview(PreviewRequest req) {
        Structure s = new Structure();
        apply(s, req.structure());
        Employee e = new Employee();
        e.setName("Preview");
        e.setMonthlyGross(req.monthlyGross() == null ? BigDecimal.ZERO : req.monthlyGross());
        e.setStructure(s);
        return calculator.calculate(e, YearMonth.now(), Map.of());
    }

    private void apply(Structure s, StructureDto d) {
        if (d.name() == null || d.name().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Structure name is required");
        if (d.basicPct() == null || d.basicPct().signum() <= 0 || d.basicPct().compareTo(BigDecimal.valueOf(100)) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Basic % must be between 1 and 100");
        s.setName(d.name().trim());
        s.setBasicPct(d.basicPct());
        if (s.getComponents() == null) s.setComponents(new java.util.ArrayList<>());
        s.getComponents().clear();
        if (d.components() != null) {
            for (ComponentDto c : d.components()) {
                StructureComponent sc = new StructureComponent();
                sc.setName(c.name());
                sc.setKind(c.kind());
                sc.setCalcType(c.calcType());
                sc.setCalcValue(c.value() == null ? BigDecimal.ZERO : c.value());
                sc.setCapAmount(c.cap());
                s.getComponents().add(sc);
            }
        }
    }

    private StructureDto toDto(Structure s) {
        return new StructureDto(s.getId(), s.getName(), s.getBasicPct(),
                s.getComponents().stream()
                        .map(c -> new ComponentDto(c.getName(), c.getKind(), c.getCalcType(), c.getCalcValue(), c.getCapAmount()))
                        .toList());
    }
}
