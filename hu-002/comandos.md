# Comandos reproducibles — HU-002

Los comandos se ejecutan en PowerShell y no modifican el código fuente. No deben cerrarse el worker ni los contenedores durante la demostración.

## 1. Iniciar el worker

Ubicarse en el microservicio, cargar localmente las variables de conexión y ejecutar:

```powershell
go run ./cmd/notification-worker
```

La terminal debe indicar que consume `scheduling.schedule.published` y `monitoring.alert.triggered`, y que health/ready escucha en `:8081`. Las credenciales y conexiones completas no se publican.

## 2. Verificar salud y dependencias

```powershell
Invoke-RestMethod -Uri "http://localhost:8081/health" -Method Get
Invoke-RestMethod -Uri "http://localhost:8081/ready" -Method Get | ConvertTo-Json -Depth 5
```

`health` demuestra que el proceso vive. `ready` comprueba el broker y la base de datos.

## 3. Construir el evento

```powershell
$event = @{
  event_id       = "55555555-5555-4555-8555-555555555555"
  event_type     = "monitoring.alert.triggered"
  source_service = "monitoring-service"
  timestamp      = (Get-Date).ToUniversalTime().ToString("o")
  version        = "1.0"
  payload        = @{
    affected_entity_type = "Learner"
    affected_entity_id   = "66666666-6666-4666-8666-666666666666"
    alert_type_code       = "LOW_ATTENDANCE"
  }
} | ConvertTo-Json -Depth 10 -Compress

$publishRequest = @{
  properties       = @{ content_type = "application/json" }
  routing_key      = "monitoring.alert.triggered"
  payload          = $event
  payload_encoding = "string"
} | ConvertTo-Json -Depth 10
```

`$event` representa el contrato. `$publishRequest` usa el formato de la API de administración de RabbitMQ.

## 4. Publicar en RabbitMQ

Después de crear localmente `$headers` con la autenticación del laboratorio:

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:15672/api/exchanges/%2F/monitoring-events/publish" `
  -Method Post `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $publishRequest | ConvertTo-Json
```

`%2F` representa el virtual host `/`. `routed: true` confirma que un binding encontró una cola.

## 5. Revisar las interfaces

- RabbitMQ: `http://localhost:15672`
- Cola: `notification-service.events`
- MailHog: `http://localhost:18025`

RabbitMQ muestra el consumidor, binding, `Ready` y `Unacked`. MailHog permite comprobar el correo sin enviarlo a Internet.

## 6. Consultar la notificación

```powershell
docker exec notification-recognition-postgres-1 psql `
  -U design_software_app `
  -d design-software-develop `
  -c "SELECT id, recipient_email, channel, send_status, source_service, source_event_id, sent_at FROM notification.sent_notification WHERE source_event_id = '55555555-5555-4555-8555-555555555555'::uuid;"
```

Es un `SELECT`; no cambia datos. Debe devolver una fila `EMAIL` y `SENT`.

## 7. Probar idempotencia

Se repite la publicación sin cambiar `$event` ni su `event_id`. Después:

```powershell
docker exec notification-recognition-postgres-1 psql `
  -U design_software_app `
  -d design-software-develop `
  -c "SELECT source_event_id, COUNT(*) AS cantidad_registros FROM notification.sent_notification WHERE source_event_id = '55555555-5555-4555-8555-555555555555'::uuid GROUP BY source_event_id;"
```

La prueba produjo una fila en PostgreSQL, pero dos correos en MailHog: idempotencia parcial.

## 8. Comprobar Outbox

```powershell
docker exec notification-recognition-postgres-1 psql `
  -U design_software_app `
  -d design-software-develop `
  -c "SELECT event_type, (published_at IS NOT NULL) AS publicado, created_at, published_at FROM notification.outbox WHERE event_type = 'notification.notification.sent' ORDER BY created_at DESC;"
```

`publicado = t` significa que el relay publicó el evento de salida.

## Seguridad

- No mostrar contraseñas ni cadenas DSN.
- No utilizar correos personales.
- No pulsar `Delete`, `Purge` o `Unbind` en RabbitMQ.
- No borrar MailHog antes de terminar la evidencia.
