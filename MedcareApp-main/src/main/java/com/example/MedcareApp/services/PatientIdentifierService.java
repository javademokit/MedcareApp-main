package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.Patient;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PatientIdentifierService {
    private static final Pattern PAN_PATTERN = Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]");
    private static final Pattern AADHAAR_PATTERN = Pattern.compile("[0-9]{12}");
    private static final Pattern POSTAL_CODE_PATTERN = Pattern.compile("[1-9][0-9]{5}");
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_NONCE_BYTES = 12;

    private final String encodedEncryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public PatientIdentifierService(
            @Value("${app.patient.identifiers.encryption-key:}") String encodedEncryptionKey) {
        this.encodedEncryptionKey = encodedEncryptionKey;
    }

    public void updateIdentifiers(
            Patient patient, String pan, String aadhaar, String postalCode) {
        if (StringUtils.hasText(pan)) {
            String normalizedPan = pan.trim().toUpperCase(Locale.ROOT);
            if (!PAN_PATTERN.matcher(normalizedPan).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Enter PAN in the format ABCDE1234F");
            }
            patient.setPatientPanEncrypted(encrypt(normalizedPan));
            patient.setPatientPanLastFour(normalizedPan.substring(normalizedPan.length() - 4));
        }

        if (StringUtils.hasText(aadhaar)) {
            String normalizedAadhaar = aadhaar.trim().replaceAll("[ -]", "");
            if (!AADHAAR_PATTERN.matcher(normalizedAadhaar).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Aadhaar number must contain exactly 12 digits");
            }
            patient.setPatientAadhaarEncrypted(encrypt(normalizedAadhaar));
            patient.setPatientAadhaarLastFour(normalizedAadhaar.substring(normalizedAadhaar.length() - 4));
        }

        if (StringUtils.hasText(postalCode)) {
            String normalizedPostalCode = postalCode.trim();
            if (!POSTAL_CODE_PATTERN.matcher(normalizedPostalCode).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Postal code must be a valid 6-digit Indian PIN code");
            }
            patient.setPatientPostalCode(normalizedPostalCode);
        }
    }

    private String encrypt(String value) {
        byte[] keyBytes = encryptionKey();
        byte[] nonce = new byte[GCM_NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            ByteBuffer encrypted = ByteBuffer.allocate(nonce.length + ciphertext.length);
            encrypted.put(nonce);
            encrypted.put(ciphertext);
            return Base64.getEncoder().encodeToString(encrypted.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt patient identifiers", exception);
        }
    }

    private byte[] encryptionKey() {
        if (!StringUtils.hasText(encodedEncryptionKey)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Patient identifier encryption is not configured on the server");
        }
        try {
            byte[] key = Base64.getDecoder().decode(encodedEncryptionKey.trim());
            if (key.length != 32) {
                throw new IllegalArgumentException("Expected a 256-bit key");
            }
            return key;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Patient identifier encryption key must be a base64-encoded 256-bit key");
        }
    }
}
