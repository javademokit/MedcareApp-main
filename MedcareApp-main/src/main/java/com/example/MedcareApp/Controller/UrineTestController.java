package com.example.MedcareApp.Controller;
import com.example.MedcareApp.Entity.test.UrineTest;
import com.example.MedcareApp.Interafce.UrineTestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/api/urinetests")
public class UrineTestController {

    @Autowired
    private UrineTestRepository urineTestRepository;

    @GetMapping
    public List<UrineTest> getAll() {
        return urineTestRepository.findAll();
    }

    @PostMapping
    public UrineTest create(@RequestBody UrineTest test) {
        return urineTestRepository.save(test);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UrineTest> getById(@PathVariable String id) {
        return urineTestRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<UrineTest> update(@PathVariable String id, @RequestBody UrineTest updated) {
        return urineTestRepository.findById(id).map(existing -> {
            updated.setId(id);
            return ResponseEntity.ok(urineTestRepository.save(updated));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        urineTestRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
