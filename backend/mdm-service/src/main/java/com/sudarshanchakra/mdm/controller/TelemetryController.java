package com.sudarshanchakra.mdm.controller;

import com.sudarshanchakra.mdm.dto.TelemetryBatchRequest;
import com.sudarshanchakra.mdm.service.TelemetryIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mdm/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final TelemetryIngestionService telemetryIngestionService;

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> batch(@Valid @RequestBody TelemetryBatchRequest req) {
        return ResponseEntity.ok(telemetryIngestionService.processBatch(req));
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Map<String, String>> heartbeat(@RequestBody Map<String, String> body) {
        telemetryIngestionService.recordHeartbeat(body);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(DateTimeException.class)
    public ResponseEntity<Map<String, String>> handleDateTimeBadRequest(DateTimeException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid date or time: " + e.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }
}
