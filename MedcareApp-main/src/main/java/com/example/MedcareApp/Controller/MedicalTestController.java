package com.example.MedcareApp.Controller;

import com.example.MedcareApp.Interafce.MedicalTestRepository;
import com.example.MedcareApp.testModel.MedicalTest;
import com.example.MedcareApp.web.DiagnosticResultRequest;
import com.example.MedcareApp.web.DiagnosticReviewRequest;
import com.mongodb.client.gridfs.model.GridFSFile;
import jakarta.validation.Valid;
import java.io.IOException;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/medical-tests")
public class MedicalTestController {
    private static final long MAX_REPORT_BYTES = 10L * 1024 * 1024;
    private final MedicalTestRepository repository;
    private final GridFsTemplate gridFsTemplate;

    public MedicalTestController(MedicalTestRepository repository, GridFsTemplate gridFsTemplate) {
        this.repository = repository;
        this.gridFsTemplate = gridFsTemplate;
    }

    @PostMapping
    public ResponseEntity<MedicalTest> saveTest(@RequestBody MedicalTest test) {
        if (test.getTestType() == null || test.getPatientName() == null || test.getPatientName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient and test type are required");
        }
        test.setStatus("ORDERED");
        test.setOrderDate(Instant.now().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(test));
    }

    @GetMapping
    public List<MedicalTest> getAll() {
        List<MedicalTest> orders = repository.findAll();
        return orders.stream().map(order -> {
            if (order.getStatus() == null || order.getStatus().isBlank()) {
                order.setStatus("ORDERED");
                if (order.getOrderDate() == null) order.setOrderDate(Instant.now().toString());
                return repository.save(order);
            }
            return order;
        }).toList();
    }

    @PostMapping("/{id}/sample")
    public MedicalTest collectSample(@PathVariable String id) {
        MedicalTest test = getOrder(id);
        if (!"ORDERED".equals(test.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only ordered tests can have a sample collected");
        }
        test.setStatus("IN_PROGRESS");
        test.setSampleCollectedAt(Instant.now().toString());
        return repository.save(test);
    }

    @PostMapping("/{id}/result")
    public MedicalTest recordResult(@PathVariable String id, @Valid @RequestBody DiagnosticResultRequest request) {
        MedicalTest test = getOrder(id);
        if (!"IN_PROGRESS".equals(test.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Collect a sample before recording a result");
        }
        test.setResultSummary(request.getResultSummary());
        test.setResultAt(Instant.now().toString());
        test.setStatus("RESULT_READY");
        return repository.save(test);
    }

    @PostMapping(value = "/{id}/report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MedicalTest uploadReport(@PathVariable String id, @RequestPart("file") MultipartFile file) {
        MedicalTest test = getOrder(id);
        if (file.isEmpty() || file.getSize() > MAX_REPORT_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a report file smaller than 10 MB");
        }
        String contentType = file.getContentType();
        if (!List.of("application/pdf", "image/png", "image/jpeg").contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PDF, PNG, or JPEG reports are allowed");
        }
        try {
            String filename = sanitizeFilename(file.getOriginalFilename());
            ObjectId reportId = gridFsTemplate.store(file.getInputStream(), filename, contentType);
            test.setReportFileId(reportId.toHexString());
            test.setReportFileName(filename);
            return repository.save(test);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the report file");
        }
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<GridFsResource> downloadReport(@PathVariable String id) {
        MedicalTest test = getOrder(id);
        if (test.getReportFileId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No report has been uploaded");
        }
        GridFSFile file = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(new ObjectId(test.getReportFileId()))));
        if (file == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report file not found");
        GridFsResource resource = gridFsTemplate.getResource(file);
        MediaType contentType = test.getReportFileName().toLowerCase().endsWith(".pdf")
                ? MediaType.APPLICATION_PDF
                : test.getReportFileName().toLowerCase().endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(test.getReportFileName()).build().toString())
                .body(resource);
    }

    @PostMapping("/{id}/review")
    public MedicalTest reviewResult(
            @PathVariable String id,
            @Valid @RequestBody DiagnosticReviewRequest request,
            Principal principal) {
        MedicalTest test = getOrder(id);
        if (!"RESULT_READY".equals(test.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only ready results can be reviewed");
        }
        test.setReviewedBy(principal.getName());
        test.setReviewedAt(Instant.now().toString());
        test.setReviewNotes(request.getReviewNotes());
        test.setStatus("REVIEWED");
        return repository.save(test);
    }

    private MedicalTest getOrder(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Test order not found"));
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "report";
        String lastPart = filename.replace('\\', '/');
        lastPart = lastPart.substring(lastPart.lastIndexOf('/') + 1);
        return lastPart.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
