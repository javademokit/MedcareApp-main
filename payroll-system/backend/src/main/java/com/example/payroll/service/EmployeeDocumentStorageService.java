package com.example.payroll.service;

import com.example.payroll.dto.Dtos.EmployeeDocumentDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

@Service
public class EmployeeDocumentStorageService {
    private static final int MAX_FILE_SIZE = 8 * 1024 * 1024;
    private static final int NONCE_SIZE = 12;
    private static final int GCM_TAG_SIZE_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${payroll.documents.storage-dir}")
    private String storageDirectory;

    @Value("${payroll.documents.key-file}")
    private String keyFile;

    public List<EmployeeDocumentDto> list(Long employeeId) {
        Path directory = employeeDirectory(employeeId);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<EmployeeDocumentDto> documents = new ArrayList<>();
        for (DocumentType type : DocumentType.values()) {
            Path encryptedFile = dataFile(directory, type);
            if (Files.exists(encryptedFile)) {
                documents.add(readMetadata(directory, type));
            }
        }
        return List.copyOf(documents);
    }

    public EmployeeDocumentDto save(Long employeeId, String documentType, MultipartFile upload) {
        DocumentType type = DocumentType.fromPath(documentType);
        if (upload == null || upload.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a document to upload");
        }
        if (upload.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Documents must be 8 MB or smaller");
        }
        byte[] plainText;
        try {
            plainText = upload.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded document");
        }
        FileFormat format = FileFormat.detect(plainText);
        Path directory = employeeDirectory(employeeId);
        Path encryptedFile = dataFile(directory, type);
        Path metadataFile = metadataFile(directory, type);
        Path encryptedTemp = null;
        Path metadataTemp = null;
        boolean dataCommitted = false;
        try {
            Files.createDirectories(directory);
            restrictPermissions(directory, EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
            byte[] encrypted = encrypt(plainText, loadOrCreateKey());
            encryptedTemp = Files.createTempFile(directory, ".employee-document-", ".tmp");
            restrictPermissions(encryptedTemp, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            Files.write(encryptedTemp, encrypted, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            Properties properties = new Properties();
            properties.setProperty("contentType", format.contentType());
            properties.setProperty("extension", format.extension());
            properties.setProperty("size", String.valueOf(plainText.length));
            properties.setProperty("uploadedAt", Instant.now().toString());
            metadataTemp = Files.createTempFile(directory, ".employee-metadata-", ".tmp");
            restrictPermissions(metadataTemp, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                properties.store(output, null);
                Files.write(metadataTemp, output.toByteArray(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            }
            Files.move(encryptedTemp, encryptedFile);
            dataCommitted = true;
            Files.move(metadataTemp, metadataFile);
            return toDto(type, properties);
        } catch (FileAlreadyExistsException exception) {
            cleanup(exception, encryptedTemp, metadataTemp, dataCommitted ? encryptedFile : null);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A " + type.label().toLowerCase(Locale.ROOT) + " is already stored. Remove it before replacing.",
                    exception);
        } catch (IOException | GeneralSecurityException exception) {
            cleanup(exception, encryptedTemp, metadataTemp, dataCommitted ? encryptedFile : null);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not store the document securely", exception);
        }
    }

    public DownloadedDocument download(Long employeeId, String documentType) {
        DocumentType type = DocumentType.fromPath(documentType);
        Path directory = employeeDirectory(employeeId);
        Path encryptedFile = dataFile(directory, type);
        if (!Files.exists(encryptedFile)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        try {
            EmployeeDocumentDto metadata = readMetadata(directory, type);
            byte[] content = decrypt(Files.readAllBytes(encryptedFile), loadOrCreateKey());
            return new DownloadedDocument(type.downloadName() + metadataExtension(directory, type), metadata.contentType(), content);
        } catch (IOException | GeneralSecurityException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read the stored document securely", exception);
        }
    }

    public void delete(Long employeeId, String documentType) {
        DocumentType type = DocumentType.fromPath(documentType);
        Path directory = employeeDirectory(employeeId);
        Path encryptedFile = dataFile(directory, type);
        if (!Files.exists(encryptedFile)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        try {
            Files.delete(encryptedFile);
            Files.deleteIfExists(metadataFile(directory, type));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not remove the stored document", exception);
        }
    }

    private Path employeeDirectory(Long employeeId) {
        if (employeeId == null || employeeId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid employee ID");
        }
        Path root = Path.of(storageDirectory).toAbsolutePath().normalize();
        Path directory = root.resolve(String.valueOf(employeeId)).normalize();
        if (!directory.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid employee ID");
        }
        return directory;
    }

    private Path dataFile(Path directory, DocumentType type) {
        return directory.resolve(type.storageName() + ".enc");
    }

    private Path metadataFile(Path directory, DocumentType type) {
        return directory.resolve(type.storageName() + ".properties");
    }

    private String metadataExtension(Path directory, DocumentType type) throws IOException {
        Properties properties = loadProperties(metadataFile(directory, type));
        String extension = properties.getProperty("extension");
        if (extension == null || !List.of(".pdf", ".jpg", ".png").contains(extension)) {
            throw new IOException("Stored document extension is invalid");
        }
        return extension;
    }

    private EmployeeDocumentDto readMetadata(Path directory, DocumentType type) {
        try {
            return toDto(type, loadProperties(metadataFile(directory, type)));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read document metadata", exception);
        }
    }

    private Properties loadProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (ByteArrayInputStream input = new ByteArrayInputStream(Files.readAllBytes(path))) {
            properties.load(input);
        }
        return properties;
    }

    private EmployeeDocumentDto toDto(DocumentType type, Properties properties) {
        try {
            return new EmployeeDocumentDto(type.pathValue(), type.label(),
                    properties.getProperty("contentType"), Long.parseLong(properties.getProperty("size")),
                    Instant.parse(properties.getProperty("uploadedAt")));
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Stored document metadata is invalid", exception);
        }
    }

    private SecretKey loadOrCreateKey() throws IOException, GeneralSecurityException {
        Path path = Path.of(keyFile).toAbsolutePath().normalize();
        Files.createDirectories(path.getParent());
        restrictPermissions(path.getParent(), EnumSet.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        if (!Files.exists(path)) {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            String encoded = Base64.getEncoder().encodeToString(generator.generateKey().getEncoded());
            try {
                Files.writeString(path, encoded, StandardCharsets.US_ASCII,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException ignored) {
                // Another request created the key first.
            }
            restrictPermissions(path, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        }
        byte[] keyBytes = Base64.getDecoder().decode(Files.readString(path, StandardCharsets.US_ASCII).trim());
        if (keyBytes.length != 32) {
            throw new GeneralSecurityException("Employee document encryption key must be 256 bits");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    private byte[] encrypt(byte[] content, SecretKey key) throws GeneralSecurityException, IOException {
        byte[] nonce = new byte[NONCE_SIZE];
        RANDOM.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_SIZE_BITS, nonce));
        byte[] ciphertext = cipher.doFinal(content);
        ByteArrayOutputStream output = new ByteArrayOutputStream(nonce.length + ciphertext.length);
        output.write(nonce);
        output.write(ciphertext);
        return output.toByteArray();
    }

    private byte[] decrypt(byte[] data, SecretKey key) throws GeneralSecurityException {
        if (data.length <= NONCE_SIZE) {
            throw new GeneralSecurityException("Stored document is incomplete");
        }
        byte[] nonce = java.util.Arrays.copyOfRange(data, 0, NONCE_SIZE);
        byte[] ciphertext = java.util.Arrays.copyOfRange(data, NONCE_SIZE, data.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_SIZE_BITS, nonce));
        return cipher.doFinal(ciphertext);
    }

    private void restrictPermissions(Path path, java.util.Set<PosixFilePermission> permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Some filesystems do not expose POSIX permission bits.
        }
    }

    private void cleanup(Throwable original, Path... paths) {
        for (Path path : paths) {
            if (path == null) continue;
            try {
                Files.deleteIfExists(path);
            } catch (IOException cleanupException) {
                original.addSuppressed(cleanupException);
            }
        }
    }

    public record DownloadedDocument(String fileName, String contentType, byte[] content) {}

    private enum DocumentType {
        AADHAAR("aadhaar", "Aadhaar card", "aadhaar-card"),
        PAN("pan", "PAN card", "pan-card"),
        SALARY_SLIP("salary-slip", "Salary slip", "salary-slip"),
        DEGREE_CERTIFICATE("degree", "Degree certificate", "degree-certificate");

        private final String pathValue;
        private final String label;
        private final String storageName;

        DocumentType(String pathValue, String label, String storageName) {
            this.pathValue = pathValue;
            this.label = label;
            this.storageName = storageName;
        }

        private static DocumentType fromPath(String value) {
            for (DocumentType type : values()) {
                if (type.pathValue.equalsIgnoreCase(value)) {
                    return type;
                }
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported employee document type");
        }

        private String pathValue() { return pathValue; }
        private String label() { return label; }
        private String storageName() { return storageName; }
        private String downloadName() { return storageName; }
    }

    private record FileFormat(String extension, String contentType) {
        private static FileFormat detect(byte[] data) {
            if (data.length >= 5 && new String(data, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
                return new FileFormat(".pdf", "application/pdf");
            }
            if (data.length >= 3 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xd8
                    && (data[2] & 0xff) == 0xff) {
                return new FileFormat(".jpg", "image/jpeg");
            }
            byte[] pngSignature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
            if (data.length >= pngSignature.length
                    && java.util.Arrays.equals(java.util.Arrays.copyOf(data, pngSignature.length), pngSignature)) {
                return new FileFormat(".png", "image/png");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Upload a genuine PDF, JPEG, or PNG document");
        }
    }
}
