package com.example.MedcareApp.Controller;

import com.example.MedcareApp.services.AmbulanceService;
import com.example.MedcareApp.web.AmbulanceLocationUpdate;
import com.example.MedcareApp.web.AmbulancePairRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ambulance/tracking")
@RequiredArgsConstructor
public class AmbulancePhoneTrackingController {
    private final AmbulanceService ambulanceService;

    @PostMapping("/pair")
    public Map<String, Object> pair(@Valid @RequestBody AmbulancePairRequest request) {
        return ambulanceService.pairDriverPhone(request.getCode());
    }

    @PostMapping("/location")
    public Map<String, Object> location(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody AmbulanceLocationUpdate request) {
        return ambulanceService.updatePhoneLocation(authorization, request);
    }
}
