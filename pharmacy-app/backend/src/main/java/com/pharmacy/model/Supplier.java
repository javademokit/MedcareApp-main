package com.pharmacy.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document("suppliers")
public class Supplier {
    @Id private String id;
    private String name;
    private String gstin;
    private String drugLicenceNo;
    private String phone;
    private String address;
}
