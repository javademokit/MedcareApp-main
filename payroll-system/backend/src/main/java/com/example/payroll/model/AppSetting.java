package com.example.payroll.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AppSetting {
    @Id
    private String settingKey;
    private String settingValue;
}
