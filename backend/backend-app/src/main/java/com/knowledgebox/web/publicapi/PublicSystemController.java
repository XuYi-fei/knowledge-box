package com.knowledgebox.web.publicapi;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public/system")
public class PublicSystemController {

    @GetMapping("/availability")
    public Map<String, Object> availability() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("reachable", true);
        response.put("timestamp", Instant.now().toString());
        return response;
    }
}
