package com.comercioflex.analytics.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.analytics.application.AnalyticsRepository.EventType;
import com.comercioflex.analytics.application.AnalyticsService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/analytics")
public class PublicAnalyticsController {

    private final AnalyticsService service;

    public PublicAnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void record(@Valid @RequestBody AnalyticsEventRequest body) {
        service.record(
                body.eventType(),
                body.visitorId(),
                body.sessionId(),
                body.path(),
                body.source(),
                body.medium(),
                body.productId());
    }

    public record AnalyticsEventRequest(
            @NotNull EventType eventType,
            @NotNull UUID visitorId,
            @NotNull UUID sessionId,
            @NotNull @Size(min = 1, max = 512) String path,
            @Size(max = 120) String source,
            @Size(max = 80) String medium,
            UUID productId) {
    }
}
