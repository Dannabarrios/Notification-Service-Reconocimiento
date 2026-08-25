# HU-004 — Resiliencia e idempotencia

## Comprensión inicial

La persistencia deduplica por `source_event_id` y el outbox usa bloqueo concurrente. Se documentarán también los límites observados.

## Código reconocido

- [Persistencia PostgreSQL](../codigo/notification-service/app/adapters/outbound/persistence.py)

## Evidencia pendiente

- Ejecutar los comandos específicos de Python/FastAPI.
- Incorporar capturas propias sin modificar el microservicio.
- Añadir el diagrama del flujo observado.
- Registrar resultado, conclusión, mejora propuesta y enlace del video.
