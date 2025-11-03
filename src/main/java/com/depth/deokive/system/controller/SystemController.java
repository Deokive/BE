package com.depth.deokive.system.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok("🟢 Deokive Server is running smoothly");
    }

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @Value("${DB_HOST:unknown}")
    private String dbHost;

    @GetMapping("/env-check")
    public ResponseEntity<?> envCheck() {
        Map<String, String> env = new HashMap<>();
        env.put("spring.profiles.active", activeProfile);
        env.put("DB_HOST", dbHost);
        return ResponseEntity.ok(env);
    }
}
