package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.emergency.AmbulanceBooking;
import com.example.MedcareApp.Entity.emergency.AmbulanceBranch;
import com.example.MedcareApp.Entity.emergency.AmbulanceVehicle;
import com.example.MedcareApp.services.AmbulanceService;
import com.example.MedcareApp.web.AmbulanceBookingRequest;
import com.example.MedcareApp.web.AmbulanceBranchRequest;
import com.example.MedcareApp.web.AmbulanceDispatchRequest;
import com.example.MedcareApp.web.AmbulanceTrackingUpdate;
import com.example.MedcareApp.web.AmbulanceVehicleRequest;
import com.example.MedcareApp.web.PaymentVerificationRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ambulance")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','CRM_EXECUTIVE',"
        + "'RECEPTIONIST','BILLING_EXECUTIVE','FINANCE')")
public class AmbulanceController {
    private final AmbulanceService ambulanceService;

    @GetMapping("/configuration")
    public Map<String, Object> configuration() {
        return ambulanceService.configuration();
    }

    @GetMapping("/patients")
    public List<Map<String, Object>> patients() {
        return ambulanceService.patients();
    }

    @GetMapping("/vehicles")
    public List<AmbulanceVehicle> vehicles() {
        return ambulanceService.vehicles();
    }

    @GetMapping("/branches")
    public List<AmbulanceBranch> branches() {
        return ambulanceService.branches();
    }

    @PostMapping("/branches")
    @ResponseStatus(HttpStatus.CREATED)
    public AmbulanceBranch createBranch(@Valid @RequestBody AmbulanceBranchRequest request) {
        return ambulanceService.createBranch(request);
    }

    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    public AmbulanceVehicle createVehicle(@Valid @RequestBody AmbulanceVehicleRequest request) {
        return ambulanceService.createVehicle(request);
    }

    @PutMapping("/vehicles/{id}")
    public AmbulanceVehicle updateVehicle(
            @PathVariable String id, @Valid @RequestBody AmbulanceVehicleRequest request) {
        return ambulanceService.updateVehicle(id, request);
    }

    @PostMapping("/vehicles/{id}/pairing-code")
    public Map<String, Object> createPairingCode(@PathVariable String id) {
        return ambulanceService.createPairingCode(id);
    }

    @GetMapping("/bookings")
    public List<AmbulanceBooking> bookings() {
        return ambulanceService.bookings();
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public AmbulanceBooking createBooking(
            @Valid @RequestBody AmbulanceBookingRequest request, Principal principal) {
        return ambulanceService.createBooking(request, principal.getName());
    }

    @PostMapping("/bookings/{id}/cash-payment")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','BILLING_EXECUTIVE','FINANCE')")
    public AmbulanceBooking confirmCashPayment(@PathVariable String id, Principal principal) {
        return ambulanceService.confirmCashPayment(id, principal.getName());
    }

    @PostMapping("/bookings/{id}/checkout")
    public Map<String, Object> createCheckout(@PathVariable String id) {
        return ambulanceService.createCheckout(id);
    }

    @PostMapping("/bookings/{id}/verify")
    public AmbulanceBooking verifyCheckout(
            @PathVariable String id, @Valid @RequestBody PaymentVerificationRequest request) {
        return ambulanceService.verifyCheckout(id, request);
    }

    @PostMapping("/bookings/{id}/cancel")
    public AmbulanceBooking cancel(@PathVariable String id) {
        return ambulanceService.cancel(id);
    }

    @PostMapping("/bookings/{id}/cash-refund")
    public AmbulanceBooking confirmCashRefund(@PathVariable String id, Principal principal) {
        return ambulanceService.confirmCashRefund(id, principal.getName());
    }

    @PutMapping("/bookings/{id}/dispatch")
    public AmbulanceBooking dispatch(
            @PathVariable String id, @Valid @RequestBody AmbulanceDispatchRequest request) {
        return ambulanceService.dispatch(id, request);
    }

    @PutMapping("/bookings/{id}/tracking")
    public AmbulanceBooking updateTracking(
            @PathVariable String id, @Valid @RequestBody AmbulanceTrackingUpdate request) {
        return ambulanceService.updateTracking(id, request);
    }
}
