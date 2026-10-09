package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.Appointment;
import com.example.MedcareApp.Entity.emergency.EmergencyCase;
import com.example.MedcareApp.Entity.hrpayroll.Employee;
import com.example.MedcareApp.Entity.nursing.Ward;
import com.example.MedcareApp.Entity.nursing.WardBed;
import com.example.MedcareApp.testModel.MedicalTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class MongoDashboardSummaryDataSource implements DashboardSummaryDataSource {
    private final MongoTemplate mongoTemplate;

    public MongoDashboardSummaryDataSource(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<Appointment> findAppointments(LocalDate firstDay, LocalDate lastDay) {
        Query query = Query.query(Criteria.where("date").gte(firstDay.toString()).lte(lastDay.toString()));
        query.fields().include("date").include("time").include("appointmentStatus");
        return mongoTemplate.find(query, Appointment.class);
    }

    @Override
    public int countOpenEmergencies() {
        return Math.toIntExact(mongoTemplate.count(
                Query.query(Criteria.where("status").ne("CLOSED")), EmergencyCase.class));
    }

    @Override
    public List<EmergencyCase> findEmergencies(Instant firstInstant, Instant nextDayInstant) {
        Query query = Query.query(Criteria.where("createdAt").gte(firstInstant).lt(nextDayInstant));
        query.fields().include("status").include("createdAt");
        return mongoTemplate.find(query, EmergencyCase.class);
    }

    @Override
    public int countTestsWithStatus(String status) {
        return Math.toIntExact(mongoTemplate.count(
                Query.query(Criteria.where("status").is(status)), MedicalTest.class));
    }

    @Override
    public List<MedicalTest> findTests(LocalDate firstDay, LocalDate lastDay) {
        Query query = Query.query(Criteria.where("orderDate")
                .gte(firstDay.toString()).lte(lastDay.toString()));
        query.fields().include("status").include("orderDate");
        return mongoTemplate.find(query, MedicalTest.class);
    }

    @Override
    public List<PatientAdmissionBucket> findActivePatientAdmissionBuckets() {
        Criteria activePatient = new Criteria().andOperator(
                Criteria.where("patientAdmitdate").exists(true).ne(""),
                new Criteria().orOperator(
                        Criteria.where("patientDischargedate").exists(false),
                        Criteria.where("patientDischargedate").is(null),
                        Criteria.where("patientDischargedate").is("")));
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(activePatient),
                Aggregation.project()
                        .and("patientAdmitdate").as("admitDate")
                        .and("patientWardnum").as("ward"),
                Aggregation.group("admitDate", "ward").count().as("count"),
                Aggregation.project("count")
                        .and("_id.admitDate").as("admitDate")
                        .and("_id.ward").as("ward"));
        AggregationResults<PatientAdmissionBucket> results = mongoTemplate.aggregate(
                aggregation, "patients", PatientAdmissionBucket.class);
        return results.getMappedResults();
    }

    @Override
    public List<Ward> findWards() {
        return mongoTemplate.findAll(Ward.class);
    }

    @Override
    public List<WardBed> findWardBeds() {
        Query query = new Query();
        query.fields().include("wardId").include("status");
        return mongoTemplate.find(query, WardBed.class);
    }

    @Override
    public int countActiveNurses() {
        return Math.toIntExact(mongoTemplate.count(Query.query(
                Criteria.where("employeeType").is("NURSE").and("status").is("ACTIVE")), Employee.class));
    }
}
