package com.example.payroll.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.example.payroll.security.EmployeeAccessInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final EmployeeAccessInterceptor employeeAccessInterceptor;

    public WebConfig(EmployeeAccessInterceptor employeeAccessInterceptor) {
        this.employeeAccessInterceptor = employeeAccessInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(employeeAccessInterceptor).addPathPatterns("/api/employees/**");
    }
}
