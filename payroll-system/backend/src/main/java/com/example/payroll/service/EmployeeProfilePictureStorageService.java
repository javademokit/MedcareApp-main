package com.example.payroll.service;

import com.example.payroll.dto.Dtos.EmployeeProfilePictureDto;
import com.example.payroll.model.EmployeeProfilePicture;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;

@Service
public class EmployeeProfilePictureStorageService {
    private static final int MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
    };

    private final MongoTemplate mongo;

    public EmployeeProfilePictureStorageService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public EmployeeProfilePictureDto save(Long employeeId, MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a profile picture to upload");
        }
        if (upload.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Profile pictures must be 5 MB or smaller");
        }

        byte[] content;
        try {
            content = upload.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the profile picture", exception);
        }
        if (content.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a profile picture to upload");
        }
        if (content.length > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Profile pictures must be 5 MB or smaller");
        }

        String contentType = detectContentType(content);
        Instant uploadedAt = Instant.now();
        EmployeeProfilePicture picture = new EmployeeProfilePicture();
        picture.setEmployeeId(employeeId);
        picture.setContentType(contentType);
        picture.setContent(content);
        picture.setUploadedAt(uploadedAt);
        mongo.save(picture);
        return new EmployeeProfilePictureDto(contentType, content.length, uploadedAt);
    }

    public StoredPicture load(Long employeeId) {
        EmployeeProfilePicture picture = mongo.findById(employeeId, EmployeeProfilePicture.class);
        if (picture == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile picture not found");
        }
        return new StoredPicture(picture.getContentType(), picture.getContent());
    }

    private String detectContentType(byte[] content) {
        if (content.length >= 3 && (content[0] & 0xff) == 0xff
                && (content[1] & 0xff) == 0xd8 && (content[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (content.length >= PNG_SIGNATURE.length
                && Arrays.equals(Arrays.copyOf(content, PNG_SIGNATURE.length), PNG_SIGNATURE)) {
            return "image/png";
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Upload a genuine JPEG or PNG profile picture");
    }

    public record StoredPicture(String contentType, byte[] content) {}
}
