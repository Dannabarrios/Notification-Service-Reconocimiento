# Evidencia end-to-end — Python/FastAPI

Fecha: 2026-08-25.

## Resultado

```json
{
  "tests": "23 passed",
  "api_health": "ok",
  "api_ready": "ok",
  "worker_health": "ok",
  "worker_ready": "ok",
  "api_post_status": "PENDING",
  "api_get_same_id": true,
  "event_id": "2d7d7235-9184-453f-94b0-5a556330b921",
  "first_routed": true,
  "duplicate_routed": true,
  "rows_for_duplicate_event": 1,
  "database_status": "SENT",
  "outbox_published": true,
  "matching_mailhog_messages": 2
}
```

Observabilidad comprobada:

- Prometheus: `http_server_requests_total` para health, ready, POST y GET.
- Prometheus: `notification_delivered_total{channel="EMAIL",status="SENT"} = 2`.
- Tempo: trazas recientes de `notification-api` y `notification-worker`.
- Terminal: logs JSON estructurados.

## Interpretación

Python conserva la misma semántica que Go y Java: la fila es idempotente por `source_event_id`, pero el correo puede duplicarse. La mejora propuesta para HU-004 debe mover el efecto externo a un flujo idempotente o usar una tabla de entregas con deduplicación antes de SMTP.

Los procesos Python locales fueron detenidos al terminar. Los contenedores existentes no se eliminaron ni se detuvieron.

