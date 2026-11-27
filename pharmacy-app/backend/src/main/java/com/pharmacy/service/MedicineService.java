package com.pharmacy.service;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.pharmacy.common.ApiException;
import com.pharmacy.model.Medicine;
import com.pharmacy.repo.Repos.MedicineRepository;
import com.pharmacy.web.Dto.MedicineRequest;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MedicineService {
    public static final Set<String> SCHEDULES = Set.of("OTC", "G", "H", "H1", "X", "NDPS");
    private final MedicineRepository repo;
    private final MongoTemplate mongo;
    private final AuditService audit;

    public List<Medicine> search(String q, int limit) {
        Criteria c = where("active").is(true);
        if (q != null && !q.isBlank()) {
            Pattern p = Pattern.compile(Pattern.quote(q.trim()), Pattern.CASE_INSENSITIVE);
            c.orOperator(where("brandName").regex(p), where("genericName").regex(p), where("barcode").is(q.trim()));
        }
        return mongo.find(query(c).with(Sort.by("brandName")).limit(limit), Medicine.class);
    }

    public Medicine get(String id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Medicine not found"));
    }

    public Medicine create(MedicineRequest r) {
        Medicine m = apply(new Medicine(), r);
        m.setActive(true);
        m = repo.save(m);
        audit.log("CREATE", "Medicine", m.getId(), m.getBrandName());
        return m;
    }

    public Medicine update(String id, MedicineRequest r) {
        Medicine m = apply(get(id), r);
        m = repo.save(m);
        audit.log("UPDATE", "Medicine", id, m.getBrandName());
        return m;
    }

    public void deactivate(String id) {
        Medicine m = get(id);
        m.setActive(false);
        repo.save(m);
        audit.log("DEACTIVATE", "Medicine", id, m.getBrandName());
    }

    private Medicine apply(Medicine m, MedicineRequest r) {
        if (!SCHEDULES.contains(r.schedule())) throw ApiException.bad("Schedule must be one of " + SCHEDULES);
        m.setBrandName(r.brandName().trim());
        m.setGenericName(r.genericName());
        m.setManufacturer(r.manufacturer());
        m.setDosageForm(r.dosageForm());
        m.setUnitsPerPack(r.unitsPerPack());
        m.setHsnCode(r.hsnCode());
        m.setGstPercent(r.gstPercent());
        m.setSchedule(r.schedule());
        m.setStorage(r.storage() == null ? "ROOM" : r.storage());
        m.setReorderLevel(r.reorderLevel());
        m.setBarcode(r.barcode());
        return m;
    }
}
