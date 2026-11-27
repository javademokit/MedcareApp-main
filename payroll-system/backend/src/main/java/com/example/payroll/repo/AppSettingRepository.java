package com.example.payroll.repo;

import com.example.payroll.model.AppSetting;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AppSettingRepository extends MongoRepository<AppSetting, String> {}
