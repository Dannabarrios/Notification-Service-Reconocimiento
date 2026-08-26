# Comandos reproducibles — HU-002 Java

## 1. Verificar el worker

El worker se ejecuta con perfil `worker` y puerto alterno `28082` para no interferir con Go.

```powershell
Invoke-RestMethod 'http://localhost:28082/health'
Invoke-RestMethod 'http://localhost:28082/ready' | ConvertTo-Json -Depth 5
```

El resultado debe mostrar `broker: true` y `database: true`.

## 2. Preparar autenticación local de RabbitMQ

Las credenciales se leen del contenedor existente y se conservan solo en memoria:

```powershell
$rabbit = docker inspect notification-recognition-rabbitmq-1 | ConvertFrom-Json
$rabbitEnv = @{}

foreach ($entry in $rabbit[0].Config.Env) {
    $parts = $entry -split '=', 2
    if ($parts.Count -eq 2) {
        $rabbitEnv[$parts[0]] = $parts[1]
    }
}

$pair = "$($rabbitEnv['RABBITMQ_DEFAULT_USER']):$($rabbitEnv['RABBITMQ_DEFAULT_PASS'])"
$token = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
$headers = @{ Authorization = "Basic $token" }
```

## 3. Construir el evento

```powershell
$event = @{
    event_id       = '22222222-2222-4222-8222-222222222202'
    event_type     = 'monitoring.alert.triggered'
    source_service = 'monitoring-service-java-demo'
    timestamp      = (Get-Date).ToUniversalTime().ToString('o')
    version        = '1.0'
    payload        = @{
        affected_entity_type = 'Learner'
        affected_entity_id   = '33333333-3333-4333-8333-333333333302'
        alert_type_code       = 'LOW_ATTENDANCE'
    }
} | ConvertTo-Json -Depth 10 -Compress

$publishRequest = @{
    properties       = @{ content_type = 'application/json' }
    routing_key      = 'monitoring.alert.triggered'
    payload          = $event
    payload_encoding = 'string'
} | ConvertTo-Json -Depth 10
```

## 4. Publicar en el exchange

```powershell
Invoke-RestMethod `
    -Uri 'http://localhost:15672/api/exchanges/%2F/monitoring-events/publish' `
    -Method Post `
    -Headers $headers `
    -ContentType 'application/json' `
    -Body $publishRequest
```

Resultado esperado: `routed: true`.

## 5. Consultar la cola

```powershell
$queue = Invoke-RestMethod `
    -Uri 'http://localhost:15672/api/queues/%2F/notification-service.events' `
    -Headers $headers

$queue | Select-Object name, state, consumers, messages, messages_ready, messages_unacknowledged
```

Después del consumo se esperan cero mensajes listos y cero sin confirmar.

## 6. Consultar PostgreSQL y Outbox

```powershell
docker exec notification-recognition-postgres-1 psql `
  -U design_software_app `
  -d design-software-develop `
  -c "SELECT n.id, n.source_event_id, n.send_status, o.event_type, (o.published_at IS NOT NULL) AS publicado FROM notification.sent_notification n LEFT JOIN notification.outbox o ON o.payload->>'notification_id'=n.id::text WHERE n.source_event_id='22222222-2222-4222-8222-222222222202'::uuid;"
```

La consulta es de solo lectura. El resultado esperado contiene `SENT`, `notification.notification.sent` y `publicado: true`.

## 7. Consultar MailHog por API

```powershell
$mail = Invoke-RestMethod 'http://localhost:18025/api/v2/messages?limit=5'
$mail.items | Select-Object -First 5 | ForEach-Object {
    [pscustomobject]@{
        subject = $_.Content.Headers.Subject[0]
        to      = $_.Content.Headers.To[0]
        created = $_.Created
    }
}
```

Debe aparecer `Alerta: LOW_ATTENDANCE` dirigido a `dev-notifications@sena.local`.
