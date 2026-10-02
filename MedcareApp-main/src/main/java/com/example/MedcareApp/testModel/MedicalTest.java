package com.example.MedcareApp.testModel;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "medical_tests")
public class MedicalTest {
    @Id
    private String id;

    private String patientId;
    private String patientName;
    private int age;
    private String gender;
    private String referredBy;
    private String category;
    private MedicalTestType testType;
    private String labTechnician;
    private String remarks;
    private String glucose;
    private String protein;
    private String ketones;
    private String ph;
    private String blood;
    private String status = "ORDERED";
    private String orderDate;
    private String sampleCollectedAt;
    private String resultSummary;
    private String resultAt;
    private String reviewedBy;
    private String reviewedAt;
    private String reviewNotes;
    private String reportFileId;
    private String reportFileName;

    public MedicalTest() {
    }

    public MedicalTest(String id, String patientName, int age, String gender, String referredBy,
                      MedicalTestType testType, String labTechnician, String remarks) {
        this.id = id;
        this.patientName = patientName;
        this.age = age;
        this.gender = gender;
        this.referredBy = referredBy;
        this.testType = testType;
        this.labTechnician = labTechnician;
        this.remarks = remarks;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPatientName() {
        return patientName;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getReferredBy() {
        return referredBy;
    }

    public void setReferredBy(String referredBy) {
        this.referredBy = referredBy;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public MedicalTestType getTestType() {
        return testType;
    }

    public void setTestType(MedicalTestType testType) {
        this.testType = testType;
    }

    public String getLabTechnician() {
        return labTechnician;
    }

    public void setLabTechnician(String labTechnician) {
        this.labTechnician = labTechnician;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getGlucose() {
        return glucose;
    }

    public void setGlucose(String glucose) {
        this.glucose = glucose;
    }

    public String getProtein() {
        return protein;
    }

    public void setProtein(String protein) {
        this.protein = protein;
    }

    public String getKetones() {
        return ketones;
    }

    public void setKetones(String ketones) {
        this.ketones = ketones;
    }

    public String getPh() {
        return ph;
    }

    public void setPh(String ph) {
        this.ph = ph;
    }

    public String getBlood() {
        return blood;
    }

    public void setBlood(String blood) {
        this.blood = blood;
    }

    public int getPrice() {
        return testType != null ? testType.getPrice() : 0;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOrderDate() { return orderDate; }
    public void setOrderDate(String orderDate) { this.orderDate = orderDate; }
    public String getSampleCollectedAt() { return sampleCollectedAt; }
    public void setSampleCollectedAt(String sampleCollectedAt) { this.sampleCollectedAt = sampleCollectedAt; }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    public String getResultAt() { return resultAt; }
    public void setResultAt(String resultAt) { this.resultAt = resultAt; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(String reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }
    public String getReportFileId() { return reportFileId; }
    public void setReportFileId(String reportFileId) { this.reportFileId = reportFileId; }
    public String getReportFileName() { return reportFileName; }
    public void setReportFileName(String reportFileName) { this.reportFileName = reportFileName; }
}
