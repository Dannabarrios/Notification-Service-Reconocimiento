package com.sena.notification_service.adapter.in.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorEnvelope(
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("message") String message,
        @JsonProperty("trace_id") String traceId) {
    static ErrorEnvelope of(String code, String message) {
        return new ErrorEnvelope(code, message, null);
    }
}
