package com.example.payroll.web;

import com.example.payroll.dto.Dtos.EmployeeDto;
import com.example.payroll.dto.Dtos.EmployeeDocumentDto;
import com.example.payroll.dto.Dtos.EmployeeProfilePictureDto;
import com.example.payroll.service.EmployeeDocumentStorageService;
import com.example.payroll.service.EmployeeProfilePictureStorageService;
import com.example.payroll.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {
    private final EmployeeService service;
    private final EmployeeDocumentStorageService documents;
    private final EmployeeProfilePictureStorageService profilePictures;

    @GetMapping public List<EmployeeDto> list() { return service.list(); }
    @GetMapping("/roles") public List<String> roles() { return service.roles(); }
    @PostMapping public EmployeeDto create(@RequestBody EmployeeDto dto) { return service.save(null, dto); }
    @PostMapping("/onboard") public EmployeeDto onboard(@RequestBody EmployeeDto dto) { return service.onboard(null, dto); }
    @PutMapping("/{id}") public EmployeeDto update(@PathVariable Long id, @RequestBody EmployeeDto dto) { return service.save(id, dto); }
    @PutMapping("/{id}/onboard") public EmployeeDto updateOnboarding(@PathVariable Long id, @RequestBody EmployeeDto dto) {
        return service.onboard(id, dto);
    }

    @GetMapping("/{id}/documents")
    public List<EmployeeDocumentDto> listDocuments(@PathVariable Long id) {
        requireEmployee(id);
        return documents.list(id);
    }

    @PostMapping(path = "/{id}/documents/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmployeeDocumentDto uploadDocument(@PathVariable Long id, @PathVariable String type,
                                             @RequestPart("file") MultipartFile file) {
        requireEmployee(id);
        return documents.save(id, type, file);
    }

    @GetMapping("/{id}/documents/{type}/download")
    public ResponseEntity<ByteArrayResource> downloadDocument(@PathVariable Long id, @PathVariable String type) {
        requireEmployee(id);
        EmployeeDocumentStorageService.DownloadedDocument document = documents.download(id, type);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .contentLength(document.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(document.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(document.content()));
    }

    @DeleteMapping("/{id}/documents/{type}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id, @PathVariable String type) {
        requireEmployee(id);
        documents.delete(id, type);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{id}/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmployeeProfilePictureDto uploadProfilePicture(
            @PathVariable Long id, @RequestPart("file") MultipartFile file) {
        requireEmployee(id);
        return profilePictures.save(id, file);
    }

    @GetMapping("/{id}/profile-picture")
    public ResponseEntity<byte[]> getProfilePicture(@PathVariable Long id) {
        requireEmployee(id);
        EmployeeProfilePictureStorageService.StoredPicture picture = profilePictures.load(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(picture.contentType()))
                .contentLength(picture.content().length)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(picture.content());
    }

    private void requireEmployee(Long id) {
        if (!service.exists(id)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Employee not found");
        }
    }
}
