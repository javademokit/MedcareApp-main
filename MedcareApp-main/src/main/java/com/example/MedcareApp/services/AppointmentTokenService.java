package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.AppointmentTokenCounter;
import com.example.MedcareApp.Entity.Appointment;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentTokenService implements ApplicationRunner {
    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        List<Appointment> appointmentsWithoutTokens = mongoTemplate.find(
                Query.query(Criteria.where("tokenNumber").is(null)), Appointment.class);
        int assignedCount = 0;
        for (Appointment appointment : appointmentsWithoutTokens.stream()
                .filter(appointment -> hasValidDate(appointment.getDate()))
                .sorted(Comparator.comparing(Appointment::getDate)
                        .thenComparing(Appointment::getTime, Comparator.nullsFirst(String::compareTo))
                        .thenComparing(Appointment::getId))
                .toList()) {
            long tokenNumber = nextNumberForDate(appointment.getDate());
            var result = mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(appointment.getId())
                            .and("tokenNumber").is(null)),
                    new Update().set("tokenNumber", tokenNumber),
                    Appointment.class);
            if (result.getMatchedCount() > 0) assignedCount++;
        }
        if (assignedCount > 0) {
            log.info("Assigned daily token numbers to {} existing appointments", assignedCount);
        }
    }

    public long nextNumberForDate(String date) {
        Query query = Query.query(Criteria.where("_id").is(date));
        Update update = new Update()
                .inc("lastNumber", 1)
                .setOnInsert("serviceDate", date);
        AppointmentTokenCounter counter;
        try {
            counter = mongoTemplate.findAndModify(
                    query, update, FindAndModifyOptions.options().upsert(true).returnNew(true),
                    AppointmentTokenCounter.class);
        } catch (DuplicateKeyException exception) {
            counter = mongoTemplate.findAndModify(
                    query, update, FindAndModifyOptions.options().returnNew(true),
                    AppointmentTokenCounter.class);
        }
        if (counter == null || counter.getLastNumber() < 1) {
            throw new IllegalStateException("Could not allocate the appointment token number");
        }
        return counter.getLastNumber();
    }

    private boolean hasValidDate(String date) {
        try {
            LocalDate.parse(date);
            return true;
        } catch (DateTimeParseException | NullPointerException exception) {
            log.warn("Skipping token assignment for an appointment with an invalid date");
            return false;
        }
    }
}
