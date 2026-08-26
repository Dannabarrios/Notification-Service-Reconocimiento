# Comandos reproducibles — HU-004 Java

Se reutilizan `$headers` y la autenticación de RabbitMQ explicados en HU-002.

## 1. Publicar un cuerpo que no es JSON

```powershell
$request = @{
    properties       = @{ content_type = 'application/json' }
    routing_key      = 'monitoring.alert.triggered'
    payload          = 'esto-no-es-json-java'
    payload_encoding = 'string'
} | ConvertTo-Json -Depth 10

Invoke-RestMethod `
  -Uri 'http://localhost:15672/api/exchanges/%2F/monitoring-events/publish' `
  -Method Post -Headers $headers -ContentType 'application/json' -Body $request
```

## 2. Publicar un evento no soportado mediante una ruta válida

```powershell
$event = @{
    event_id       = '55555555-5555-4555-8555-555555555504'
    event_type     = 'monitoring.event.unsupported'
    source_service = 'recognition-resilience-java'
    timestamp      = (Get-Date).ToUniversalTime().ToString('o')
    version        = '1.0'
    payload        = @{ value = 'demo' }
} | ConvertTo-Json -Depth 10 -Compress

$request = @{
    properties       = @{ content_type = 'application/json' }
    routing_key      = 'monitoring.alert.triggered'
    payload          = $event
    payload_encoding = 'string'
} | ConvertTo-Json -Depth 10

Invoke-RestMethod `
  -Uri 'http://localhost:15672/api/exchanges/%2F/monitoring-events/publish' `
  -Method Post -Headers $headers -ContentType 'application/json' -Body $request
```

La routing key permite que el mensaje llegue a la cola; el `event_type` interno provoca el error del caso de uso.

## 3. Consultar la cola después de cada prueba

```powershell
$queue = Invoke-RestMethod `
  'http://localhost:15672/api/queues/%2F/notification-service.events' `
  -Headers $headers

$queue | Select-Object consumers, messages, messages_ready, messages_unacknowledged
```

## 4. Repetir el evento de HU-002

Publicar nuevamente el mismo envelope de HU-002 sin cambiar `event_id`.

Antes y después se cuenta MailHog:

```powershell
$before = (Invoke-RestMethod 'http://localhost:18025/api/v2/messages?limit=1').total
# Publicar otra vez el evento 22222222-2222-4222-8222-222222222202
Start-Sleep -Seconds 3
$after = (Invoke-RestMethod 'http://localhost:18025/api/v2/messages?limit=1').total
"Cambio SMTP: $($after - $before)"
```

## 5. Contar persistencia y Outbox

```powershell
docker exec notification-recognition-postgres-1 psql `
  -U design_software_app `
  -d design-software-develop `
  -c "SELECT COUNT(*) AS notificaciones FROM notification.sent_notification WHERE source_event_id='22222222-2222-4222-8222-222222222202'::uuid; SELECT COUNT(*) AS eventos_outbox FROM notification.outbox o JOIN notification.sent_notification n ON o.payload->>'notification_id'=n.id::text WHERE n.source_event_id='22222222-2222-4222-8222-222222222202'::uuid;"
```
