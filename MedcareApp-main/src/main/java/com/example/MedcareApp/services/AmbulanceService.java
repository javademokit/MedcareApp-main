package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Entity.emergency.AmbulanceBooking;
import com.example.MedcareApp.Entity.emergency.AmbulanceBranch;
import com.example.MedcareApp.Entity.emergency.AmbulanceVehicle;
import com.example.MedcareApp.Interafce.AmbulanceBranchRepository;
import com.example.MedcareApp.Interafce.AmbulanceBookingRepository;
import com.example.MedcareApp.Interafce.AmbulanceVehicleRepository;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.web.AmbulanceBookingRequest;
import com.example.MedcareApp.web.AmbulanceBranchRequest;
import com.example.MedcareApp.web.AmbulanceDispatchRequest;
import com.example.MedcareApp.web.AmbulanceLocationUpdate;
import com.example.MedcareApp.web.AmbulanceTrackingUpdate;
import com.example.MedcareApp.web.AmbulanceVehicleRequest;
import com.example.MedcareApp.web.PaymentVerificationRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AmbulanceService {
    private static final List<String> BOOKING_STATUSES =
            List.of("BOOKED", "DISPATCHED", "EN_ROUTE", "ARRIVED", "COMPLETED", "CANCELLED");
    private static final Map<String, List<String>> NEXT_STATUSES = Map.of(
            "DISPATCHED", List.of("EN_ROUTE"),
            "EN_ROUTE", List.of("ARRIVED"),
            "ARRIVED", List.of("COMPLETED"));

    private final AmbulanceBookingRepository bookingRepository;
    private final AmbulanceBranchRepository branchRepository;
    private final AmbulanceVehicleRepository vehicleRepository;
    private final PatientRepository patientRepository;
    private final RestClient.Builder restClientBuilder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${ambulance.rate-per-kilometer:100}")
    private BigDecimal ratePerKilometer;
    @Value("${PAYMENTS_RAZORPAY_KEY_ID:}")
    private String razorpayKeyId;
    @Value("${PAYMENTS_RAZORPAY_KEY_SECRET:}")
    private String razorpaySecret;

    public Map<String, Object> configuration() {
        if (ratePerKilometer == null || ratePerKilometer.signum() <= 0) {
            throw new IllegalStateException("Ambulance rate per kilometer must be greater than zero");
        }
        return Map.of(
                "ratePerKilometer", ratePerKilometer,
                "onlinePaymentAvailable", StringUtils.hasText(razorpayKeyId)
                        && StringUtils.hasText(razorpaySecret));
    }

    public List<Map<String, Object>> patients() {
        return patientRepository.findAll().stream()
                .filter(patient -> StringUtils.hasText(patient.getPatientId()))
                .map(patient -> Map.<String, Object>of(
                        "patientId", patient.getPatientId(),
                        "patientName", patient.getPatientName() == null ? "" : patient.getPatientName(),
                        "mobile", patient.getPatientmobileNo() == null ? "" : patient.getPatientmobileNo(),
                        "address", patient.getPatientAddress() == null ? "" : patient.getPatientAddress()))
                .toList();
    }

    public List<AmbulanceVehicle> vehicles() {
        return vehicleRepository.findAllByOrderByRegistrationNumberAsc();
    }

    public List<AmbulanceBranch> branches() {
        return branchRepository.findAllByOrderByNameAsc();
    }

    public AmbulanceBranch createBranch(AmbulanceBranchRequest request) {
        String name = request.getName().trim();
        if (branchRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A branch with this name already exists");
        }
        AmbulanceBranch branch = new AmbulanceBranch();
        branch.setName(name);
        branch.setAddress(request.getAddress() == null ? "" : request.getAddress().trim());
        return branchRepository.save(branch);
    }

    public AmbulanceVehicle createVehicle(AmbulanceVehicleRequest request) {
        String registration = request.getRegistrationNumber().trim().toUpperCase(Locale.ROOT);
        if (vehicleRepository.existsByRegistrationNumberIgnoreCase(registration)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An ambulance already uses this registration number");
        }
        AmbulanceVehicle vehicle = new AmbulanceVehicle();
        vehicle.setRegistrationNumber(registration);
        setVehicleBranch(vehicle, request.getBranchId());
        vehicle.setVehicleType(request.getVehicleType().trim());
        vehicle.setDriverName(request.getDriverName().trim());
        vehicle.setDriverPhone(request.getDriverPhone().trim());
        String status = StringUtils.hasText(request.getStatus())
                ? request.getStatus().trim().toUpperCase(Locale.ROOT) : vehicle.getStatus();
        if (!List.of("AVAILABLE", "OUT_OF_SERVICE").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vehicle status must be AVAILABLE or OUT_OF_SERVICE");
        }
        vehicle.setStatus(status);
        vehicle.setUpdatedAt(Instant.now());
        return vehicleRepository.save(vehicle);
    }

    public AmbulanceVehicle updateVehicle(String id, AmbulanceVehicleRequest request) {
        AmbulanceVehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ambulance vehicle not found"));
        if (!List.of("AVAILABLE", "OUT_OF_SERVICE").contains(vehicle.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vehicle details cannot be edited while an ambulance is on a trip");
        }
        String registration = request.getRegistrationNumber().trim().toUpperCase(Locale.ROOT);
        if (vehicleRepository.existsByRegistrationNumberIgnoreCaseAndIdNot(registration, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An ambulance already uses this registration number");
        }
        String status = StringUtils.hasText(request.getStatus())
                ? request.getStatus().trim().toUpperCase(Locale.ROOT) : vehicle.getStatus();
        if (!List.of("AVAILABLE", "OUT_OF_SERVICE").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vehicle status must be AVAILABLE or OUT_OF_SERVICE");
        }
        vehicle.setRegistrationNumber(registration);
        setVehicleBranch(vehicle, request.getBranchId());
        vehicle.setVehicleType(request.getVehicleType().trim());
        vehicle.setDriverName(request.getDriverName().trim());
        vehicle.setDriverPhone(request.getDriverPhone().trim());
        vehicle.setStatus(status);
        vehicle.setUpdatedAt(Instant.now());
        return vehicleRepository.save(vehicle);
    }

    public List<AmbulanceBooking> bookings() {
        return bookingRepository.findAllByOrderByCreatedAtDesc();
    }

    public AmbulanceBooking createBooking(AmbulanceBookingRequest request, String createdBy) {
        String patientId = request.getPatientId().trim();
        List<Patient> patients = patientRepository.findAllByPatientId(patientId);
        if (patients.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient ID not found");
        if (patients.size() != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Patient ID is not unique");
        if (request.getDistanceKm().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Distance must be greater than zero");
        }
        String method = request.getPaymentMethod().trim().toUpperCase(Locale.ROOT);
        if (!List.of("CASH", "RAZORPAY").contains(method)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment method must be CASH or RAZORPAY");
        }
        if (method.equals("RAZORPAY") && !razorpayConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Online payment is unavailable because Razorpay is not configured");
        }
        BigDecimal rate = configuration().get("ratePerKilometer") instanceof BigDecimal value
                ? value : ratePerKilometer;
        AmbulanceBooking booking = new AmbulanceBooking();
        booking.setPatientId(patientId);
        if (StringUtils.hasText(request.getBranchId())) {
            AmbulanceBranch branch = findBranch(request.getBranchId());
            booking.setBranchId(branch.getId());
            booking.setBranchName(branch.getName());
        }
        booking.setPatientName(patients.get(0).getPatientName());
        booking.setPatientMobile(patients.get(0).getPatientmobileNo());
        booking.setPickupAddress(request.getPickupAddress().trim());
        booking.setDropAddress(request.getDropAddress().trim());
        booking.setDistanceKm(request.getDistanceKm().setScale(2, RoundingMode.HALF_UP));
        booking.setRatePerKm(rate.setScale(2, RoundingMode.HALF_UP));
        booking.setAmount(booking.getDistanceKm().multiply(booking.getRatePerKm())
                .setScale(2, RoundingMode.HALF_UP));
        booking.setPaymentMethod(method);
        booking.setCreatedBy(createdBy);
        booking.setStatus(method.equals("CASH") ? "BOOKED" : "AWAITING_PAYMENT");
        booking.setPaymentStatus("PENDING");
        if (method.equals("CASH")) {
            AmbulanceBooking.Payment payment = new AmbulanceBooking.Payment();
            payment.setAmount(booking.getAmount());
            payment.setMethod("CASH");
            payment.setStatus("PENDING");
            booking.getPayments().add(payment);
        }
        return saveBooking(booking);
    }

    public AmbulanceBooking confirmCashPayment(String bookingId, String actor) {
        AmbulanceBooking booking = findBooking(bookingId);
        if (!"CASH".equals(booking.getPaymentMethod()) || !"BOOKED".equals(booking.getStatus())
                || !"PENDING".equals(booking.getPaymentStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This booking is not awaiting cash payment confirmation");
        }
        AmbulanceBooking.Payment payment = booking.getPayments().stream()
                .filter(attempt -> "CASH".equals(attempt.getMethod()) && "PENDING".equals(attempt.getStatus()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "No pending cash payment is available to confirm"));
        Instant receivedAt = Instant.now();
        payment.setStatus("RECEIVED");
        payment.setReceivedBy(actor);
        payment.setReceivedAt(receivedAt);
        booking.setPaidAmount(booking.getAmount());
        booking.setPaymentStatus("PAID");
        booking.setUpdatedAt(receivedAt);
        return saveBooking(booking);
    }

    public Map<String, Object> createCheckout(String bookingId) {
        requireRazorpayConfiguration();
        AmbulanceBooking booking = findBooking(bookingId);
        requirePayable(booking);
        expirePendingAttempts(booking);
        if (booking.getPayments().stream().anyMatch(payment -> "PENDING".equals(payment.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An online payment attempt is already pending");
        }
        AmbulanceBooking.Payment attempt = new AmbulanceBooking.Payment();
        attempt.setAmount(booking.getAmount().subtract(booking.getPaidAmount()).setScale(2, RoundingMode.HALF_UP));
        attempt.setMethod("RAZORPAY");
        attempt.setProvider("RAZORPAY");
        attempt.setStatus("PENDING");
        try {
            RazorpayOrder response = restClientBuilder.build().post().uri("https://api.razorpay.com/v1/orders")
                    .headers(headers -> headers.setBasicAuth(razorpayKeyId, razorpaySecret))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", attempt.getAmount().movePointRight(2).intValueExact(),
                            "currency", booking.getCurrency(), "receipt", booking.getBookingNumber()))
                    .retrieve().body(RazorpayOrder.class);
            if (response == null || !StringUtils.hasText(response.id())) {
                throw new IllegalStateException("Razorpay did not return an order ID");
            }
            attempt.setGatewayOrderId(response.id());
            booking.getPayments().add(attempt);
            saveBooking(booking);
            return Map.of("provider", "RAZORPAY", "keyId", razorpayKeyId, "orderId", response.id(),
                    "amount", attempt.getAmount().movePointRight(2).intValueExact(),
                    "currency", booking.getCurrency(), "name", booking.getPatientName(),
                    "description", "Ambulance booking " + booking.getBookingNumber());
        } catch (RuntimeException exception) {
            if (exception instanceof ResponseStatusException statusException) throw statusException;
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not create an online ambulance payment", exception);
        }
    }

    public AmbulanceBooking verifyCheckout(String bookingId, PaymentVerificationRequest request) {
        requireRazorpayConfiguration();
        AmbulanceBooking booking = findBooking(bookingId);
        if ("CANCELLED".equals(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled ambulance bookings cannot be paid");
        }
        AmbulanceBooking.Payment attempt = booking.getPayments().stream()
                .filter(payment -> request.getGatewayOrderId().equals(payment.getGatewayOrderId()))
                .findFirst().orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Ambulance payment attempt not found"));
        if ("RECEIVED".equals(attempt.getStatus())) return booking;
        if (!"PENDING".equals(attempt.getStatus())
                || !"RAZORPAY".equalsIgnoreCase(request.getProvider())
                || !StringUtils.hasText(request.getGatewayPaymentId())
                || !StringUtils.hasText(request.getSignature())
                || !constantTimeEquals(hmacSha256(razorpaySecret,
                        request.getGatewayOrderId() + "|" + request.getGatewayPaymentId()), request.getSignature())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Razorpay payment signature could not be verified");
        }
        attempt.setStatus("RECEIVED");
        attempt.setGatewayPaymentId(request.getGatewayPaymentId());
        attempt.setReceivedAt(Instant.now());
        booking.setPaidAmount(booking.getPaidAmount().add(attempt.getAmount()).setScale(2, RoundingMode.HALF_UP));
        booking.setPaymentStatus(booking.getPaidAmount().compareTo(booking.getAmount()) >= 0 ? "PAID" : "PARTIALLY_PAID");
        if ("PAID".equals(booking.getPaymentStatus())) booking.setStatus("BOOKED");
        booking.setUpdatedAt(Instant.now());
        return saveBooking(booking);
    }

    public AmbulanceBooking dispatch(String bookingId, AmbulanceDispatchRequest request) {
        AmbulanceBooking booking = findBooking(bookingId);
        if (!"PAID".equals(booking.getPaymentStatus()) || !"BOOKED".equals(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Collect the full ambulance fare before dispatching");
        }
        AmbulanceVehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ambulance vehicle not found"));
        if (!"AVAILABLE".equals(vehicle.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Selected ambulance is not available");
        }
        if (StringUtils.hasText(booking.getBranchId())
                && !booking.getBranchId().equals(vehicle.getBranchId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Assign an ambulance from the booking's selected branch");
        }
        vehicle.setStatus("ON_TRIP");
        vehicle.setUpdatedAt(Instant.now());
        vehicleRepository.save(vehicle);
        booking.setVehicleId(vehicle.getId());
        booking.setVehicleRegistration(vehicle.getRegistrationNumber());
        booking.setDriverName(vehicle.getDriverName());
        booking.setDriverPhone(vehicle.getDriverPhone());
        booking.setCurrentLocation(StringUtils.hasText(vehicle.getCurrentLocation())
                ? vehicle.getCurrentLocation() : booking.getPickupAddress());
        booking.setTrackingNote(request.getTrackingNote());
        booking.setStatus("DISPATCHED");
        booking.setUpdatedAt(Instant.now());
        return saveBooking(booking);
    }

    public AmbulanceBooking updateTracking(String bookingId, AmbulanceTrackingUpdate request) {
        AmbulanceBooking booking = findBooking(bookingId);
        List<String> allowedNext = NEXT_STATUSES.getOrDefault(booking.getStatus(), List.of());
        if (!allowedNext.contains(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ambulance tracking must progress from Dispatched to En route, Arrived, then Completed");
        }
        booking.setStatus(request.getStatus());
        if (StringUtils.hasText(request.getCurrentLocation())) {
            booking.setCurrentLocation(request.getCurrentLocation().trim());
        }
        booking.setTrackingNote(StringUtils.hasText(request.getTrackingNote())
                ? request.getTrackingNote().trim() : booking.getTrackingNote());
        booking.setUpdatedAt(Instant.now());
        AmbulanceBooking saved = saveBooking(booking);
        if (StringUtils.hasText(saved.getVehicleId())) {
            vehicleRepository.findById(saved.getVehicleId()).ifPresent(vehicle -> {
                if (StringUtils.hasText(request.getCurrentLocation())) {
                    vehicle.setCurrentLocation(request.getCurrentLocation().trim());
                }
                vehicle.setUpdatedAt(Instant.now());
                vehicleRepository.save(vehicle);
            });
        }
        if ("COMPLETED".equals(saved.getStatus()) && StringUtils.hasText(saved.getVehicleId())) {
            vehicleRepository.findById(saved.getVehicleId()).ifPresent(vehicle -> {
                vehicle.setStatus("AVAILABLE");
                vehicle.setCurrentLocation(saved.getDropAddress());
                vehicle.setUpdatedAt(Instant.now());
                vehicleRepository.save(vehicle);
            });
        }
        return saved;
    }

    public Map<String, Object> createPairingCode(String vehicleId) {
        AmbulanceVehicle vehicle = findVehicle(vehicleId);
        byte[] codeBytes = new byte[12];
        secureRandom.nextBytes(codeBytes);
        String code = HexFormat.of().formatHex(codeBytes).toUpperCase(Locale.ROOT);
        vehicle.setPairingCodeHash(sha256(code));
        vehicle.setPairingCodeExpiresAt(Instant.now().plus(java.time.Duration.ofMinutes(10)));
        vehicle.setLocationTokenHash(null);
        vehicleRepository.save(vehicle);
        return Map.of("code", code, "expiresAt", vehicle.getPairingCodeExpiresAt());
    }

    public Map<String, Object> pairDriverPhone(String code) {
        String normalizedCode = code.trim().replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        String codeHash = sha256(normalizedCode);
        Instant now = Instant.now();
        AmbulanceVehicle vehicle = vehicleRepository.findByPairingCodeHash(codeHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Pairing code is invalid or expired"));
        if (vehicle.getPairingCodeExpiresAt() == null || !vehicle.getPairingCodeExpiresAt().isAfter(now)) {
            vehicle.setPairingCodeHash(null);
            vehicle.setPairingCodeExpiresAt(null);
            vehicleRepository.save(vehicle);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Pairing code is invalid or expired");
        }
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        vehicle.setLocationTokenHash(sha256(token));
        vehicle.setPairingCodeHash(null);
        vehicle.setPairingCodeExpiresAt(null);
        vehicleRepository.save(vehicle);
        return Map.of("vehicleId", vehicle.getId(), "registrationNumber", vehicle.getRegistrationNumber(),
                "driverName", vehicle.getDriverName(), "locationToken", token);
    }

    public Map<String, Object> updatePhoneLocation(String authorization, AmbulanceLocationUpdate request) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid tracking token is required");
        }
        String tokenHash = sha256(authorization.substring(7).trim());
        AmbulanceVehicle vehicle = vehicleRepository.findByLocationTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Tracking token is invalid or has been revoked"));
        Instant now = Instant.now();
        vehicle.setLatitude(request.getLatitude());
        vehicle.setLongitude(request.getLongitude());
        vehicle.setLocationAccuracyMeters(request.getAccuracyMeters());
        vehicle.setCurrentLocation(request.getLatitude().toPlainString() + ", "
                + request.getLongitude().toPlainString());
        vehicle.setLocationUpdatedAt(now);
        vehicle.setUpdatedAt(now);
        vehicleRepository.save(vehicle);
        return Map.of("receivedAt", now);
    }

    public AmbulanceBooking cancel(String bookingId) {
        AmbulanceBooking booking = findBooking(bookingId);
        if (!List.of("AWAITING_PAYMENT", "BOOKED").contains(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An ambulance booking cannot be cancelled after dispatch");
        }
        if (booking.getPaidAmount().signum() > 0) {
            if ("CASH".equals(booking.getPaymentMethod())) {
                booking.setRefundStatus("REFUND_DUE");
                booking.setRefundAmount(booking.getPaidAmount());
            } else {
                refundOnlinePayment(booking);
            }
        } else {
            booking.setRefundStatus("NOT_REQUIRED");
        }
        booking.getPayments().stream()
                .filter(payment -> "PENDING".equals(payment.getStatus()))
                .forEach(payment -> payment.setStatus("CANCELLED"));
        booking.setStatus("CANCELLED");
        booking.setPaymentStatus(booking.getPaidAmount().signum() == 0 ? "CANCELLED"
                : "REFUNDED".equals(booking.getRefundStatus()) ? "REFUNDED" : "REFUND_PENDING");
        booking.setUpdatedAt(Instant.now());
        AmbulanceBooking saved = saveBooking(booking);
        if (StringUtils.hasText(saved.getVehicleId())) {
            vehicleRepository.findById(saved.getVehicleId()).ifPresent(vehicle -> {
                vehicle.setStatus("AVAILABLE");
                vehicle.setUpdatedAt(Instant.now());
                vehicleRepository.save(vehicle);
            });
        }
        return saved;
    }

    public AmbulanceBooking confirmCashRefund(String bookingId, String actor) {
        AmbulanceBooking booking = findBooking(bookingId);
        if (!"CANCELLED".equals(booking.getStatus()) || !"CASH".equals(booking.getPaymentMethod())
                || !"REFUND_DUE".equals(booking.getRefundStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking has no cash refund due");
        }
        booking.setRefundStatus("REFUNDED");
        booking.setPaymentStatus("REFUNDED");
        booking.setRefundedBy(actor);
        booking.setRefundedAt(Instant.now());
        booking.setUpdatedAt(Instant.now());
        booking.getPayments().stream()
                .filter(payment -> "CASH".equals(payment.getMethod()) && "RECEIVED".equals(payment.getStatus()))
                .forEach(payment -> payment.setStatus("REFUNDED"));
        return saveBooking(booking);
    }

    private void refundOnlinePayment(AmbulanceBooking booking) {
        requireRazorpayConfiguration();
        AmbulanceBooking.Payment payment = booking.getPayments().stream()
                .filter(attempt -> "RECEIVED".equals(attempt.getStatus())
                        && "RAZORPAY".equals(attempt.getProvider())
                        && StringUtils.hasText(attempt.getGatewayPaymentId()))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No verified Razorpay payment is available to refund"));
        try {
            RazorpayRefund response = restClientBuilder.build().post()
                    .uri("https://api.razorpay.com/v1/payments/{id}/refund", payment.getGatewayPaymentId())
                    .headers(headers -> headers.setBasicAuth(razorpayKeyId, razorpaySecret))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", booking.getPaidAmount().movePointRight(2).intValueExact()))
                    .retrieve().body(RazorpayRefund.class);
            if (response == null || !StringUtils.hasText(response.id())) {
                throw new IllegalStateException("Razorpay did not confirm the refund request");
            }
            if ("failed".equalsIgnoreCase(response.status())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Razorpay declined the ambulance refund; booking was not cancelled");
            }
            payment.setGatewayRefundId(response.id());
            boolean processed = "processed".equalsIgnoreCase(response.status());
            payment.setStatus(processed ? "REFUNDED" : "REFUND_PENDING");
            booking.setRefundStatus(processed ? "REFUNDED" : "REFUND_PENDING");
            booking.setRefundAmount(booking.getPaidAmount());
            if (processed) booking.setRefundedAt(Instant.now());
        } catch (RuntimeException exception) {
            if (exception instanceof ResponseStatusException statusException) throw statusException;
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Razorpay could not refund the ambulance payment; booking was not cancelled", exception);
        }
    }

    private void requirePayable(AmbulanceBooking booking) {
        if (!"AWAITING_PAYMENT".equals(booking.getStatus())
                || booking.getPaidAmount().compareTo(booking.getAmount()) >= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ambulance booking is not waiting for payment");
        }
    }

    private void setVehicleBranch(AmbulanceVehicle vehicle, String branchId) {
        if (!StringUtils.hasText(branchId)) {
            vehicle.setBranchId(null);
            vehicle.setBranchName(null);
            return;
        }
        AmbulanceBranch branch = findBranch(branchId);
        vehicle.setBranchId(branch.getId());
        vehicle.setBranchName(branch.getName());
    }

    private AmbulanceBranch findBranch(String id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ambulance branch not found"));
    }

    private AmbulanceVehicle findVehicle(String id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ambulance vehicle not found"));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void expirePendingAttempts(AmbulanceBooking booking) {
        Instant cutoff = Instant.now().minus(java.time.Duration.ofMinutes(30));
        booking.getPayments().stream()
                .filter(payment -> "PENDING".equals(payment.getStatus())
                        && payment.getCreatedAt() != null && payment.getCreatedAt().isBefore(cutoff))
                .forEach(payment -> payment.setStatus("EXPIRED"));
    }

    private boolean razorpayConfigured() {
        return StringUtils.hasText(razorpayKeyId) && StringUtils.hasText(razorpaySecret);
    }

    private void requireRazorpayConfiguration() {
        if (!razorpayConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Online ambulance payment is unavailable because Razorpay is not configured");
        }
    }

    private AmbulanceBooking findBooking(String id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ambulance booking not found"));
    }

    private AmbulanceBooking saveBooking(AmbulanceBooking booking) {
        try {
            return bookingRepository.save(booking);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ambulance booking changed during processing. Refresh and try again.", exception);
        }
    }

    private String hmacSha256(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not verify Razorpay ambulance payment", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private record RazorpayOrder(String id) {}
    private record RazorpayRefund(String id, String status) {}
}
