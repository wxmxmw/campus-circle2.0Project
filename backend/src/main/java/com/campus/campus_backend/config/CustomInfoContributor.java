package com.campus.campus_backend.config;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
public class CustomInfoContributor implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> details = new HashMap<>();

        // 应用基本信息
        details.put("name", "Campus Circle");
        details.put("version", "1.0.0");
        details.put("description", "Campus community API service");

        // 运行时信息
        details.put("startup-time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        details.put("java-version", System.getProperty("java.version"));
        details.put("spring-boot-version", "3.3.5");

        // 功能模块信息
        Map<String, Object> modules = new HashMap<>();
        modules.put("authentication", "JWT authentication");
        modules.put("social", "Community content");
        modules.put("messaging", "Messaging and notifications");
        modules.put("errands", "Campus errands");
        modules.put("monitoring", "System monitoring");
        details.put("modules", modules);

        // 部署环境信息
        Map<String, Object> deployment = new HashMap<>();
        deployment.put("server", "Windows Server");
        deployment.put("web-server", "IIS + ARR");
        deployment.put("database", "MySQL 8.0");
        deployment.put("cache", "Redis 6.0");
        details.put("deployment", deployment);

        builder.withDetails(details);
    }
}