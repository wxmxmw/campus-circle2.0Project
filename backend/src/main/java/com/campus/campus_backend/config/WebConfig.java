package com.campus.campus_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${upload.path:/uploads}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
        String uploadLocation = uploadDir.toUri().toString();

        registry.addResourceHandler("/api/uploads/**")
                .addResourceLocations(uploadLocation);

        // Keep older stored URLs working if they were saved without the /api prefix.
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadLocation);
    }
}
