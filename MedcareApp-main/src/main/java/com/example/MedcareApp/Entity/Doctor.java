package com.example.MedcareApp.Entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
@Data
@Document(collection = "doctors")
public class Doctor {

    @Id
    private String id;
    private String employeeId;
    private String doctorName;
    private String doctorSpecialistName;
    private String doctorMobileNo;
    private String doctorDestination;
    private List<String> doctorAvailabletime;  // Store slots as a list of strings
    private int doctorslot;  // Number of slots
    private double doctorfee;


}
