package com.sena.notification_service.adapter.in.http;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Profile("api")
public class ApiExceptionHandler {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorEnvelope> malformedJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ErrorEnvelope.of(
                "VALIDATION_ERROR", "payload invalido: JSON no valido"));
    }
}
