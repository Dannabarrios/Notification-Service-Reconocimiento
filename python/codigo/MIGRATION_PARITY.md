# Matriz de paridad Go → Python/FastAPI

| Área | Go original | Python/FastAPI | Estado |
|---|---|---|---|
| Composition root API | `cmd/notification-api/main.go` | `app/entrypoints/api.py` | Equivalente |
| Composition root worker | `cmd/notification-worker/main.go` | `app/entrypoints/worker.py` | Equivalente |
| `GET /health` | `adapter/in/http/handler.go` | `adapters/inbound/http.py` | Equivalente |
| `GET /ready` | handler + worker health server | `create_app` / `create_health_app` | Equivalente |
| `POST /notifications` | `handler.send` | FastAPI `send_notification` | Equivalente |
| `GET /notifications/{id}` | `handler.get` | FastAPI `get_notification` | Equivalente |
| Validación UUID cero | `uuid.Nil` → missing | `UUID(int=0)` → missing | Equivalente |
| Error validation | `400 VALIDATION_ERROR` | `400 VALIDATION_ERROR` | Equivalente |
| Error dependencia | `503 DEPENDENCY_UNAVAILABLE` | `503 DEPENDENCY_UNAVAILABLE` | Equivalente |
| Not found | `404 NOT_FOUND` | `404 NOT_FOUND` | Equivalente |
| Entidad `SentNotification` | `domain/model/notification.go` | `domain/models.py` | Equivalente |
| Templates | `template.go` + renderer | `NotificationTemplate` + `render_template` | Equivalente |
| Send use case | `usecase/send_notification.go` | `application/use_cases.py` | Equivalente |
| Get use case | `usecase/get_notification.go` | `application/use_cases.py` | Equivalente |
| Consume event use case | `usecase/consume_domain_event.go` | `application/use_cases.py` | Equivalente |
| PostgreSQL | `pgx` | `psycopg 3` + pool | Misma BD/SQL |
| `notification.sent_notification` | existente | sin cambios | Idéntica |
| `notification.notification_template` | existente | sin cambios | Idéntica |
| `notification.outbox` | existente | sin cambios | Idéntica |
| Idempotencia | índice `source_event_id` | mismo índice + mismo `ON CONFLICT` | Equivalente |
| Consumer RabbitMQ | `amqp091-go` | `pika` | Equivalente |
| Exchanges inbound | scheduling/monitoring | mismos nombres | Equivalente |
| Cola | `notification-service.events` | mismo nombre | Equivalente |
| Envelope inválido | NACK sin requeue | NACK sin requeue | Equivalente |
| Use-case error válido | log + ACK | log + ACK | Equivalente |
| SMTP | `net/smtp` | `smtplib` | Equivalente |
| IN_APP | no-op | no-op | Equivalente |
| Recipient resolver | stub configurable | stub configurable | Equivalente |
| Outbox relay | poll 2s, limit 20 | poll 2s, limit 20 | Equivalente |
| Lock outbox | `FOR UPDATE SKIP LOCKED` | mismo SQL | Equivalente |
| Evento outbound | `notification.notification.sent` | mismo tipo | Equivalente |
| Exchange outbound | `notification-events` | mismo exchange | Equivalente |
| Trace context | W3C TraceContext+Baggage | W3C TraceContext+Baggage | Equivalente |
| Métrica HTTP count | `http.server.requests` | mismo nombre | Equivalente |
| Métrica HTTP duration | `http.server.request.duration` | mismo nombre | Equivalente |
| Métrica delivery | `notification.delivered` | mismo nombre | Equivalente |
| Logging | JSON + trace/span IDs | JSON + trace/span IDs | Equivalente |
| DB Docker/Liquibase | original | mismo contenido; CRLF/LF normalizado | Equivalente |
| Docker infra base | original | sin editar | Idéntico |
| Integración FastAPI | Dockerfiles Go | dos Dockerfiles Python + overlay compose | Sustitución necesaria |

## Alcance no ampliado

No se implementaron componentes que el Go declara fuera del alcance:

- retries/backoff/DLQ;
- integración real con actors-service;
- fan-out a `instructor_ids`;
- nuevos canales como SMS;
- cambios de esquema, tablas o constraints;
- endpoints de actualización/eliminación que el microservicio original no posee.
