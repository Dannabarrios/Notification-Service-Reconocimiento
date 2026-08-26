# Comandos — HU-004 Python

Publicar en `monitoring-events` con routing key válida:

1. Payload `esto-no-es-json-python`.
2. Envelope válido con `event_type='monitoring.event.unsupported'`.
3. Repetir exactamente el evento de HU-002.

Después consultar logs, cola, conteo de MailHog y:

```sql
SELECT COUNT(*) FROM notification.sent_notification
WHERE source_event_id='20202020-2020-4020-8020-202020202002'::uuid;
```
