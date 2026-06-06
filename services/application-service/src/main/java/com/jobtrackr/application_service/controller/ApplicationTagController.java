package com.jobtrackr.application_service.controller;

import com.jobtrackr.application_service.dto.response.ApplicationResponse;
import com.jobtrackr.application_service.service.ApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications/{id}/tag")
@RequiredArgsConstructor
public class ApplicationTagController {

    private final ApplicationService applicationService;

    @PostMapping
    public ResponseEntity<ApplicationResponse> addTag(@PathVariable UUID id, @RequestBody Map<String, String> body,
                                                      HttpServletRequest httpRequest) {
        String tag = body.get("tag");
        return ResponseEntity.ok(applicationService.addTag(id, tag, httpRequest));
    }

    @DeleteMapping("/{tag}")
    public ResponseEntity<ApplicationResponse> removeTag(@PathVariable UUID id, @PathVariable String tag,
                                                      HttpServletRequest httpRequest) {
        return ResponseEntity.ok(applicationService.removeTag(id, tag, httpRequest));
    }
}
