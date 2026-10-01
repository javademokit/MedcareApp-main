package com.example.MedcareApp.Entity;


import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.Id;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;


@Document(collection = "user")
public class user {

    @Id
    private String id;
    private  String userId;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private  String password;
    private String emailId;
    private String mobileNo;

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
        return Objects.equals(userId, user.userId) && Objects.equals(password, user.password) && Objects.equals(emailId, user.emailId) && Objects.equals(mobileNo, user.mobileNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, password, emailId, mobileNo);
    }


}