# design-software-notification-service — FastAPI

Reimplementación en Python/FastAPI del microservicio Go `design-software-notification-service-develop`.

## Arquitectura

```text
app/domain             entidades y lógica pura
app/application        comandos, puertos y casos de uso
app/adapters/inbound   FastAPI + RabbitMQ consumer
app/adapters/outbound  PostgreSQL + SMTP/IN_APP + outbox publisher + recipient stub
app/platform           logging JSON + OpenTelemetry
app/entrypoints        composition roots API/worker
```

Dependencias de conexión requeridas, igual que el Go:

- API: `NOTIFICATION_DB_DSN`.
- Worker: `NOTIFICATION_DB_DSN`, `NOTIFICATION_AMQP_URL`, `NOTIFICATION_SMTP_ADDR`.

Defaults no sensibles conservados:

- `PORT=8080`
- `WORKER_HEALTH_PORT=8081`
- `NOTIFICATION_SMTP_FROM=notifications@sena.local`
- `NOTIFICATION_RECIPIENT_STUB_EMAIL=dev-notifications@sena.local`
- `NOTIFICATION_DEPLOYMENT_ENVIRONMENT=develop`
- `OTEL_EXPORTER_OTLP_ENDPOINT=localhost:4317` en ejecución local
- `OTEL_EXPORTER_OTLP_INSECURE=true`

## Comandos

```bash
pip install -r requirements-dev.txt
pytest -q
python -m app.entrypoints.api
python -m app.entrypoints.worker
```

La guía Docker completa está en el `README.md` de la raíz del entregable.
