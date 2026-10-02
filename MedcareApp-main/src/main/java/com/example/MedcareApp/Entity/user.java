package com.example.MedcareApp.Entity;


import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.Id;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Set;
import java.util.Locale;
import java.util.stream.Collectors;


@Document(collection = "user")
public class user {

    @Id
    private String id;
    private  String userId;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private  String password;
    private String emailId;
    private String mobileNo;
    private String doctorId;
    private Set<String> roles = Set.of("PATIENT");
    private Boolean active = true;

    public user(String password, String userId, String emailId, String mobileNo) {
        this.password = password;
        this.userId = userId;
        this.emailId = emailId;
        this.mobileNo = mobileNo;
    }

    public user() {

    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setEmailId(String emailId) {
        this.emailId = emailId;
    }

    public void setMobileNo(String mobileNo) {
        this.mobileNo = mobileNo;
    }

    public String getUserId() {
        return userId;
    }

    public String getPassword() {
        return password;
    }

    public String getEmailId() {
        return emailId;
    }

    public String getMobileNo() {
        return mobileNo;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(String doctorId) {
        this.doctorId = doctorId;
    }

    public Set<String> getRoles() {
        if (roles == null || roles.isEmpty()) return Set.of("PATIENT");
        Set<String> normalizedRoles = roles.stream()
                .filter(Objects::nonNull)
                .map(role -> role.trim().toUpperCase(Locale.ROOT).replaceFirst("^ROLE_", ""))
                .filter(role -> !role.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        return normalizedRoles.isEmpty() ? Set.of("PATIENT") : normalizedRoles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles == null || roles.isEmpty() ? Set.of("PATIENT") : Set.copyOf(roles);
    }

    public boolean isActive() {
        return active == null || active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return "user{" +
                "userId='" + userId + '\'' +
                ", emailId='" + emailId + '\'' +
                ", mobileNo='" + mobileNo + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        user user = (user) o;
        return Objects.equals(userId, user.userId) && Objects.equals(password, user.password)
                && Objects.equals(emailId, user.emailId) && Objects.equals(mobileNo, user.mobileNo)
                && Objects.equals(getRoles(), user.getRoles()) && Objects.equals(isActive(), user.isActive());
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, password, emailId, mobileNo, getRoles(), isActive());
    }


}