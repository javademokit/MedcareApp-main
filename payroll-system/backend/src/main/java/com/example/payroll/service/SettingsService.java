package com.example.payroll.service;

import com.example.payroll.model.AppSetting;
import com.example.payroll.repo.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettingsService {
    private static final String AUTO_RUN = "autoRun";
    private final AppSettingRepository repo;

    public boolean autoRun() {
        return repo.findById(AUTO_RUN).map(s -> Boolean.parseBoolean(s.getSettingValue())).orElse(true);
    }

    public void setAutoRun(boolean enabled) {
        repo.save(new AppSetting(AUTO_RUN, String.valueOf(enabled)));
    }
}
