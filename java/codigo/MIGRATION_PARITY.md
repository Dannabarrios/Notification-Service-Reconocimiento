# Matriz de equivalencia Go -> Java

| Capacidad original | Implementacion Java | Estado |
|---|---|---|
| `GET /health` | `HealthController` | Conservada |
| `GET /ready` API: PostgreSQL | `DatabaseReadinessCheck` | Conservada |
| `GET /ready` worker: PostgreSQL + RabbitMQ | `DatabaseReadinessCheck` + `RabbitReadinessCheck` | Conservada |
| `POST /notifications` -> 202 | `NotificationController` + `SendNotificationService` | Conservada |
| Validaciones y envelopes de error | `NotificationController` + `ApiExceptionHandler` | Conservada |
| `GET /notifications/{id}` | `NotificationController` + `GetNotificationService` | Conservada |
| Plantillas `{{variable}}` | `TemplateRenderer` + `JdbcTemplateRepository` | Conservada |
| Fallback si plantilla no existe/falla | casos de uso Java | Conservada |
| PostgreSQL schema `notification` | DDL Liquibase original | Sin cambios |
| Estado inicial `PENDING` para POST | `SendNotificationService` | Conservada |
| Rabbit exchanges `scheduling-events` / `monitoring-events` | `RabbitTopologyConfiguration` | Conservada |
| Queue durable `notification-service.events` | `RabbitTopologyConfiguration` | Conservada |
| Routing keys de scheduling/monitoring | `RabbitTopologyConfiguration` | Conservada |
| Rechazo sin requeue de envelope invalido | `DomainEventConsumer` | Conservada |
| ACK de envelope valido aun si falla caso de uso | `DomainEventConsumer` | Conservada |
| Resolver de destinatario stub | `StubRecipientResolver` | Conservada |
| SMTP sin autenticacion hacia MailHog | `SmtpNotifier` | Conservada |
| Body SMTP igual al subject | `SmtpNotifier` | Conservada |
| Adapter `IN_APP` no-op | `InAppNotifier` | Conservada |
| Resultado `SENT`/`FAILED` | `ConsumeDomainEventService` | Conservada |
| Idempotencia por `source_event_id` | indice existente + `ON CONFLICT ... DO NOTHING` | Conservada |
| Persistencia + outbox atomicos | `JdbcNotificationRepository.saveWithOutbox` | Conservada |
| Evento `notification.notification.sent` | `ConsumeDomainEventService` | Conservada |
| Relay cada 2 s, batch 20 | `OutboxRelay` | Conservada |
| `FOR UPDATE SKIP LOCKED` | `OutboxRelay` | Conservada |
| Exchange `notification-events` topic durable | `RabbitTopologyConfiguration` | Conservada |
| Publicacion at-least-once del outbox | transaccion `OutboxRelay` | Conservada |
| `trace_parent` atraviesa el outbox | consumer -> payload -> header AMQP | Conservada |
| Metrica `notification.delivered{channel,status}` | `CompositeNotifier` / Micrometer | Conservada |
| Metricas/trazas HTTP RED | instrumentacion automatica Spring MVC + Micrometer/OpenTelemetry | Equivalente funcional (nombres de instrumentos gestionados por framework) |
| Dos procesos API/worker | perfiles Spring `api` y `worker` sobre la misma imagen | Equivalente |
| Docker PostgreSQL/Liquibase/RabbitMQ/MailHog/OTel | compose original + overlay Java | Conservada |

## Decisiones deliberadamente no cambiadas

- No se agregaron tablas ni columnas.
- No se agrego DLQ/retry porque tampoco forma parte del comportamiento Go actual.
- El worker sigue enviando por canal `EMAIL` para los dos eventos soportados.
- `scheduling.schedule.published` sigue notificando solo a `published_by`; no se agrego fan-out.
- La idempotencia se mantiene en persistencia mediante `source_event_id`, igual que el repositorio Go.
