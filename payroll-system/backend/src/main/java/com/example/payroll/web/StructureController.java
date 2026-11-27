package com.example.payroll.web;

import com.example.payroll.dto.Dtos.*;
import com.example.payroll.service.StructureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/structures")
@RequiredArgsConstructor
public class StructureController {
    private final StructureService service;

    @GetMapping public List<StructureDto> list() { return service.list(); }
    @PostMapping public StructureDto create(@RequestBody StructureDto dto) { return service.save(null, dto); }
    @PutMapping("/{id}") public StructureDto update(@PathVariable Long id, @RequestBody StructureDto dto) { return service.save(id, dto); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id) { service.delete(id); return ResponseEntity.noContent().build(); }
    @PostMapping("/preview") public PayrollRow preview(@RequestBody PreviewRequest req) { return service.preview(req); }
}
