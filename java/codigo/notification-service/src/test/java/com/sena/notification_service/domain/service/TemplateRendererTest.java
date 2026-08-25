package com.sena.notification_service.domain.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemplateRendererTest {
    @Test
    void replacesKnownPlaceholdersAndKeepsUnknownOnes() {
        String result = TemplateRenderer.render(
                "Horario {{schedule_name}} - ficha {{ficha}} - {{unknown}}",
                Map.of("schedule_name", "Mañana", "ficha", "2999999"));

        assertEquals("Horario Mañana - ficha 2999999 - {{unknown}}", result);
    }
}
