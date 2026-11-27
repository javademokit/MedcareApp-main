package com.pharmacy.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data @NoArgsConstructor @AllArgsConstructor
@Document("counters")
public class Counter {
    @Id private String id;
    private long seq;
}
