package com.example.MedcareApp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.MedcareApp.Entity.Patient;
import com.example.MedcareApp.Entity.emergency.AmbulanceBooking;
import com.example.MedcareApp.Entity.emergency.AmbulanceVehicle;
import com.example.MedcareApp.Interafce.AmbulanceBranchRepository;
import com.example.MedcareApp.Interafce.AmbulanceBookingRepository;
import com.example.MedcareApp.Interafce.AmbulanceVehicleRepository;
import com.example.MedcareApp.Interafce.PatientRepository;
import com.example.MedcareApp.web.AmbulanceBookingRequest;
import com.example.MedcareApp.web.AmbulanceDispatchRequest;
import com.example.MedcareApp.web.AmbulanceLocationUpdate;
import com.example.MedcareApp.web.AmbulanceVehicleRequest;
import com.example.MedcareApp.web.PaymentVerificationRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class AmbulanceServiceTest {
    @Test
    void confirmsCashCollectionManuallyBeforeRecordingFullRefundOnCancellation() {
        AmbulanceBookingRepository bookings = mock(AmbulanceBookingRepository.class);
        AmbulanceVehicleRepository vehicles = mock(AmbulanceVehicleRepository.class);
        PatientRepository patients = mock(PatientRepository.class);
        Patient patient = patient();
        when(patients.findAllByPatientId("PT-101")).thenReturn(List.of(patient));
        when(bookings.save(any(AmbulanceBooking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AmbulanceService service = service(bookings, vehicles, patients);

        AmbulanceBooking booking = service.createBooking(request("CASH"), "crm@example.test");
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertEquals(new BigDecimal("2.00"), booking.getDistanceKm());
        assertEquals(new BigDecimal("100.00"), booking.getRatePerKm());
        assertEquals(new BigDecimal("200.00"), booking.getAmount());
        assertEquals(BigDecimal.ZERO, booking.getPaidAmount());
        assertEquals("BOOKED", booking.getStatus());
        assertEquals("PENDING", booking.getPaymentStatus());
        assertEquals("PT-101", booking.getPatientId());

        AmbulanceBooking paid = service.confirmCashPayment(booking.getId(), "cashier@example.test");
        assertEquals("PAID", paid.getPaymentStatus());
        assertEquals(new BigDecimal("200.00"), paid.getPaidAmount());
        assertEquals("RECEIVED", paid.getPayments().get(0).getStatus());
        assertEquals("cashier@example.test", paid.getPayments().get(0).getReceivedBy());
        assertEquals(paid.getPayments().get(0).getReceivedAt(), paid.getUpdatedAt());

        AmbulanceBooking cancelled = service.cancel(booking.getId());

        assertEquals("CANCELLED", cancelled.getStatus());
        assertEquals("REFUND_PENDING", cancelled.getPaymentStatus());
        assertEquals("REFUND_DUE", cancelled.getRefundStatus());
        assertEquals(new BigDecimal("200.00"), cancelled.getRefundAmount());
        AmbulanceBooking refunded = service.confirmCashRefund(booking.getId(), "cashier@example.test");
        assertEquals("REFUNDED", refunded.getRefundStatus());
        assertEquals("REFUNDED", refunded.getPaymentStatus());
        assertEquals("cashier@example.test", refunded.getRefundedBy());
    }

    @Test
    void cannotDispatchCashBookingBeforeBillingConfirmsReceipt() {
        AmbulanceBookingRepository bookings = mock(AmbulanceBookingRepository.class);
        AmbulanceVehicleRepository vehicles = mock(AmbulanceVehicleRepository.class);
        PatientRepository patients = mock(PatientRepository.class);
        when(patients.findAllByPatientId("PT-101")).thenReturn(List.of(patient()));
        when(bookings.save(any(AmbulanceBooking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AmbulanceService service = service(bookings, vehicles, patients);
        AmbulanceBooking booking = service.createBooking(request("CASH"), "crm@example.test");
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.dispatch(booking.getId(), new AmbulanceDispatchRequest()));

        assertEquals(409, exception.getStatusCode().value());
        assertEquals("PENDING", booking.getPaymentStatus());
    }

    @Test
    void verifiedOnlinePaymentIsImmediatelyRecordedAsPaidAndReceipted() throws Exception {
        AmbulanceBookingRepository bookings = mock(AmbulanceBookingRepository.class);
        AmbulanceBooking booking = new AmbulanceBooking();
        booking.setId("online-booking");
        booking.setAmount(new BigDecimal("200.00"));
        booking.setPaidAmount(BigDecimal.ZERO);
        booking.setStatus("AWAITING_PAYMENT");
        booking.setPaymentStatus("PENDING");
        AmbulanceBooking.Payment payment = new AmbulanceBooking.Payment();
        payment.setAmount(new BigDecimal("200.00"));
        payment.setMethod("RAZORPAY");
        payment.setProvider("RAZORPAY");
        payment.setStatus("PENDING");
        payment.setGatewayOrderId("order_1");
        booking.getPayments().add(payment);
        when(bookings.findById("online-booking")).thenReturn(Optional.of(booking));
        when(bookings.save(any(AmbulanceBooking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AmbulanceService service = service(
                bookings, mock(AmbulanceVehicleRepository.class), mock(PatientRepository.class));
        ReflectionTestUtils.setField(service, "razorpayKeyId", "test-key");
        ReflectionTestUtils.setField(service, "razorpaySecret", "test-secret");
        String signature = hmacSha256("test-secret", "order_1|payment_1");
        PaymentVerificationRequest request = new PaymentVerificationRequest();
        request.setProvider("RAZORPAY");
        request.setGatewayOrderId("order_1");
        request.setGatewayPaymentId("payment_1");
        request.setSignature(signature);

        AmbulanceBooking verified = service.verifyCheckout("online-booking", request);

        assertEquals("PAID", verified.getPaymentStatus());
        assertEquals("BOOKED", verified.getStatus());
        assertEquals(new BigDecimal("200.00"), verified.getPaidAmount());
        assertEquals("RECEIVED", payment.getStatus());
        assertEquals("payment_1", payment.getGatewayPaymentId());
        assertNotNull(payment.getReceivedAt());
    }

    @Test
    void rejectsBookingForUnknownPatient() {
        AmbulanceBookingRepository bookings = mock(AmbulanceBookingRepository.class);
        PatientRepository patients = mock(PatientRepository.class);
        when(patients.findAllByPatientId("PT-101")).thenReturn(List.of());
        AmbulanceService service = service(
                bookings, mock(AmbulanceVehicleRepository.class), patients);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.createBooking(request("CASH"), "crm@example.test"));

        assertEquals(404, exception.getStatusCode().value());
        verify(bookings, never()).save(any(AmbulanceBooking.class));
    }

    @Test
    void requiresPaymentBeforeDispatch() {
        AmbulanceBookingRepository bookings = mock(AmbulanceBookingRepository.class);
        AmbulanceBooking unpaid = new AmbulanceBooking();
        unpaid.setId("booking-1");
        unpaid.setStatus("AWAITING_PAYMENT");
        unpaid.setPaymentStatus("PENDING");
        when(bookings.findById("booking-1")).thenReturn(Optional.of(unpaid));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service(bookings, mock(AmbulanceVehicleRepository.class),
                        mock(PatientRepository.class)).dispatch("booking-1", new AmbulanceDispatchRequest()));

        assertEquals(409, exception.getStatusCode().value());
    }

    @Test
    void vehicleAvailabilityChangesArePersisted() {
        AmbulanceVehicleRepository vehicles = mock(AmbulanceVehicleRepository.class);
        when(vehicles.save(any(AmbulanceVehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AmbulanceService service = service(
                mock(AmbulanceBookingRepository.class), vehicles, mock(PatientRepository.class));
        AmbulanceVehicleRequest request = vehicleRequest("OUT_OF_SERVICE");

        AmbulanceVehicle created = service.createVehicle(request);

        assertEquals("OUT_OF_SERVICE", created.getStatus());
        when(vehicles.findById(created.getId())).thenReturn(Optional.of(created));
        request.setStatus("AVAILABLE");

        AmbulanceVehicle updated = service.updateVehicle(created.getId(), request);

        assertEquals("AVAILABLE", updated.getStatus());
        verify(vehicles, times(2)).save(created);
    }

    @Test
    void pairsDriverPhoneAndAcceptsLocationUsingOneTimeCode() {
        AmbulanceVehicleRepository vehicles = mock(AmbulanceVehicleRepository.class);
        AmbulanceVehicle vehicle = new AmbulanceVehicle();
        vehicle.setRegistrationNumber("AMB-101");
        vehicle.setDriverName("Driver");
        when(vehicles.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(vehicles.findByPairingCodeHash(anyString())).thenReturn(Optional.of(vehicle));
        when(vehicles.findByLocationTokenHash(anyString())).thenReturn(Optional.of(vehicle));
        when(vehicles.save(any(AmbulanceVehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AmbulanceService service = service(
                mock(AmbulanceBookingRepository.class), vehicles, mock(PatientRepository.class));

        String code = (String) service.createPairingCode(vehicle.getId()).get("code");
        var paired = service.pairDriverPhone(code);

        assertEquals(vehicle.getId(), paired.get("vehicleId"));
        assertNull(vehicle.getPairingCodeHash());
        assertThrows(ResponseStatusException.class, () -> service.pairDriverPhone(code));

        AmbulanceLocationUpdate location = new AmbulanceLocationUpdate();
        location.setLatitude(new BigDecimal("12.971600"));
        location.setLongitude(new BigDecimal("77.594600"));
        location.setAccuracyMeters(new BigDecimal("5"));
        service.updatePhoneLocation("Bearer " + paired.get("locationToken"), location);

        assertEquals(new BigDecimal("12.971600"), vehicle.getLatitude());
        assertEquals(new BigDecimal("77.594600"), vehicle.getLongitude());
        assertEquals("12.971600, 77.594600", vehicle.getCurrentLocation());
    }

    private AmbulanceService service(
            AmbulanceBookingRepository bookings,
            AmbulanceVehicleRepository vehicles,
            PatientRepository patients) {
        AmbulanceService service = new AmbulanceService(bookings,
                mock(AmbulanceBranchRepository.class), vehicles, patients, mock(RestClient.Builder.class));
        ReflectionTestUtils.setField(service, "ratePerKilometer", new BigDecimal("100"));
        return service;
    }

    private Patient patient() {
        Patient patient = new Patient();
        patient.setPatientId("PT-101");
        patient.setPatientName("Aadi Patient");
        patient.setPatientmobileNo("5551001010");
        patient.setPatientAddress("12 Main Road");
        return patient;
    }

    private AmbulanceBookingRequest request(String method) {
        AmbulanceBookingRequest request = new AmbulanceBookingRequest();
        request.setPatientId("PT-101");
        request.setPickupAddress("12 Main Road");
        request.setDropAddress("City Hospital");
        request.setDistanceKm(new BigDecimal("2"));
        request.setPaymentMethod(method);
        return request;
    }

    private AmbulanceVehicleRequest vehicleRequest(String status) {
        AmbulanceVehicleRequest request = new AmbulanceVehicleRequest();
        request.setRegistrationNumber("AMB-101");
        request.setVehicleType("BASIC");
        request.setDriverName("Driver");
        request.setDriverPhone("5551001011");
        request.setStatus(status);
        return request;
    }

    private String hmacSha256(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
