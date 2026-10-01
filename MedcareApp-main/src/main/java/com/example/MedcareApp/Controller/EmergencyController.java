package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.emergency.EmergencyCase;
import com.example.MedcareApp.Interafce.EmergencyCaseRepository;
import com.example.MedcareApp.services.EmergencyService;
import com.example.MedcareApp.web.EmergencyCaseUpdate;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/emergency/cases")
@RequiredArgsConstructor
public class EmergencyController {
    private final EmergencyService emergencyService;

    @GetMapping
    public List<EmergencyCase> getCases() {
        return emergencyService.getCases();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmergencyCase createCase(@Valid @RequestBody EmergencyCase emergencyCase) {
        return emergencyService.createCase(emergencyCase);
    }

    @PatchMapping("/{id}")
    public EmergencyCase updateCase(@PathVariable String id, @RequestBody EmergencyCaseUpdate update) {
        return emergencyService.updateCase(id, update);
    }
}
