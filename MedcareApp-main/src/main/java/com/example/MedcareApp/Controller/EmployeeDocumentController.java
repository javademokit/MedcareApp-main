package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Entity.hrpayroll.Employee;
import com.example.MedcareApp.services.HrPayrollService;
import com.mongodb.client.gridfs.model.GridFSFile;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/employees/{employeeId}/documents")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN','CLINIC_ADMIN','HR')")
public class EmployeeDocumentController {
    private static final long MAX_DOCUMENT_BYTES = 10L * 1024 * 1024;
    private static final Set<String> DOCUMENT_TYPES = Set.of("PAN_CARD", "AADHAAR_CARD", "EXPERIENCE_LETTER");
    private static final Map<String, MediaType> ALLOWED_TYPES = Map.of(
            "application/pdf", MediaType.APPLICATION_PDF,
            "image/png", MediaType.IMAGE_PNG,
            "image/jpeg", MediaType.IMAGE_JPEG);

    private final HrPayrollService service;
    private final GridFsTemplate gridFsTemplate;

    public EmployeeDocumentController(HrPayrollService service, GridFsTemplate gridFsTemplate) {
        this.service = service;
        this.gridFsTemplate = gridFsTemplate;
    }

    @PostMapping(value = "/{documentType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> upload(
            @PathVariable String employeeId,
            @PathVariable String documentType,
            @RequestPart("file") MultipartFile file) {
        String normalizedType = documentType.toUpperCase(Locale.ROOT);
        if (!DOCUMENT_TYPES.contains(normalizedType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported employee document type");
        }
        if (file.isEmpty() || file.getSize() > MAX_DOCUMENT_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a document smaller than 10 MB");
        }
        String contentType = file.getContentType();
        MediaType mediaType = ALLOWED_TYPES.get(contentType);
        if (mediaType == null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only PDF, PNG, or JPEG documents are allowed");
        }
        String fileName = sanitizeFilename(file.getOriginalFilename());
        service.employee(employeeId);
        try {
            ObjectId fileId = gridFsTemplate.store(file.getInputStream(), fileName, contentType);
            try {
                return service.attachEmployeeDocument(
                        employeeId, normalizedType, fileId.toHexString(), fileName, contentType);
            } catch (RuntimeException exception) {
                gridFsTemplate.delete(Query.query(Criteria.where("_id").is(fileId)));
                throw exception;
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store employee document");
        }
    }

    @GetMapping("/{documentType}")
    public ResponseEntity<GridFsResource> download(
            @PathVariable String employeeId,
            @PathVariable String documentType) {
        String normalizedType = documentType.toUpperCase(Locale.ROOT);
        if (!DOCUMENT_TYPES.contains(normalizedType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported employee document type");
        }
        Employee employee = service.employee(employeeId);
        Map<String, String> metadata = employee.getOnboardingDocuments() == null
                ? null : employee.getOnboardingDocuments().get(normalizedType);
        if (metadata == null || metadata.get("fileId") == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No employee document has been uploaded");
        }
        ObjectId fileId;
        try {
            fileId = new ObjectId(metadata.get("fileId"));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee document was not found");
        }
        GridFSFile file = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(fileId)));
        if (file == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee document was not found");
        MediaType mediaType = ALLOWED_TYPES.get(metadata.get("contentType"));
        if (mediaType == null) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Employee document type is not supported");
        GridFsResource resource = gridFsTemplate.getResource(file);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(metadata.get("fileName")).build().toString())
                .body(resource);
    }

    private static String sanitizeFilename(String filename) {
        String safe = filename == null ? "employee-document" : filename.replace('\\', '/');
        safe = safe.substring(safe.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank() || safe.equals(".") || safe.equals("..")) return "employee-document";
        return safe.length() > 120 ? safe.substring(safe.length() - 120) : safe;
    }
}
