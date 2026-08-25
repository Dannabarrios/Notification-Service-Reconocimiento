package com.sena.notification_service.adapter.in.health;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
public class HealthController {
    private final List<ReadinessCheck> checks;

    public HealthController(List<ReadinessCheck> checks) {
        this.checks = checks;
    }

    @GetMapping("/health")
    public Status health() {
        return new Status("ok");
    }

    @GetMapping("/ready")
    public ResponseEntity<ReadinessResponse> ready() {
        List<CheckResult> results = checks.stream()
                .map(HealthController::execute)
                .sorted(Comparator.comparing(CheckResult::name))
                .toList();
        boolean allOk = results.stream().allMatch(CheckResult::ok);
        ReadinessResponse body = new ReadinessResponse(allOk ? "ok" : "degraded", results);
        return ResponseEntity.status(allOk ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private static CheckResult execute(ReadinessCheck check) {
        try {
            check.check();
            return new CheckResult(check.name(), true, null);
        } catch (RuntimeException ex) {
            return new CheckResult(check.name(), false, ex.getMessage());
        }
    }

    public record Status(String status) {}
    public record ReadinessResponse(String status, List<CheckResult> checks) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CheckResult(String name, boolean ok, String error) {}
}
