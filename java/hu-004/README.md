# HU-004 — Resiliencia e idempotencia

## Comprensión inicial

La persistencia deduplica por `source_event_id` y el outbox usa bloqueo concurrente. Se documentarán también los límites observados.

## Código reconocido

- [Repositorio JDBC](../codigo/notification-service/src/main/java/com/sena/notification_service/adapter/out/persistence/JdbcNotificationRepository.java)

## Evidencia pendiente

- Ejecutar los comandos específicos de Java/Spring Boot.
- Incorporar capturas propias sin modificar el microservicio.
- Añadir el diagrama del flujo observado.
- Registrar resultado, conclusión, mejora propuesta y enlace del video.
