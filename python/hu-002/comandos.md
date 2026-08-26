# Comandos — HU-002 Python

```powershell
Invoke-RestMethod 'http://localhost:38082/ready' | ConvertTo-Json -Depth 5
```

Construir un envelope `monitoring.alert.triggered`, envolverlo para la API de RabbitMQ con `routing_key`, `payload` y `payload_encoding='string'`, y publicar en:

```text
POST http://localhost:15672/api/exchanges/%2F/monitoring-events/publish
```

Consultar después MailHog (`/api/v2/messages`), la cola (`/api/queues/%2F/notification-service.events`) y PostgreSQL por `source_event_id`.
