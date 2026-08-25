# Evidencia end-to-end — Java

Fecha: 2026-08-25.

## Resultado

```json
{
  "api_health": "ok",
  "api_ready": "ok",
  "worker_health": "ok",
  "worker_ready": "ok",
  "api_post_status": "PENDING",
  "api_get_same_id": true,
  "event_id": "56066ee9-7f71-4cb7-8a22-bda68b22deb3",
  "first_routed": true,
  "duplicate_routed": true,
  "rows_for_duplicate_event": 1,
  "database_status": "SENT",
  "outbox_published": true,
  "matching_mailhog_messages": 2
}
```

Observabilidad comprobada:

- Prometheus: 40 series HTTP de `notification-api`/`notification-worker`.
- Tempo: 4 trazas recientes de API después de corregir el endpoint OTLP.
- Actuator: `http.server.requests` registró GET/POST con 200/202.

La configuración OTLP se ajustó a las variables oficiales de Spring Boot: `OTEL_EXPORTER_OTLP_ENDPOINT` y `OTEL_TRACES_EXPORTER=otlp`.

## Interpretación

El duplicado demuestra dos propiedades distintas:

1. Idempotencia de persistencia: una sola fila por `source_event_id`.
2. Entrega SMTP no idempotente: dos correos porque el envío sucede antes de confirmar el conflicto.

Por eso no debe afirmarse que el servicio ofrece entrega exactamente una vez.

Los procesos Java locales fueron detenidos al terminar. Los contenedores existentes no se eliminaron ni se detuvieron.

