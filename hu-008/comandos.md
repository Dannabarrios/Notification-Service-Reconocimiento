# Comandos reproducibles — HU-008

## 1. Verificar contenedores

```powershell
docker ps `
  --filter "name=notification-recognition" `
  --format "table {{.Names}}`t{{.Status}}`t{{.Ports}}"
```

Solo lista los contenedores del laboratorio; no altera su estado.

## 2. Construir el evento

```powershell
$e2eEvent = @{
  event_id       = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
  event_type     = "scheduling.schedule.published"
  source_service = "scheduling-service"
  timestamp      = (Get-Date).ToUniversalTime().ToString("o")
  version        = "1.0"
  payload        = @{
    published_by  = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
    schedule_name = "Agosto-2026"
    ficha         = "3145555"
  }
} | ConvertTo-Json -Depth 10 -Compress

$e2ePublishRequest = @{
  properties       = @{ content_type = "application/json" }
  routing_key      = "scheduling.schedule.published"
  payload          = $e2eEvent
  payload_encoding = "string"
} | ConvertTo-Json -Depth 10
```

## 3. Publicar en RabbitMQ

Después de preparar localmente `$headers` sin mostrar su valor:

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:15672/api/exchanges/%2F/scheduling-events/publish" `
  -Method Post `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $e2ePublishRequest | ConvertTo-Json
```

El resultado esperado es `routed: true`. Un `401` indica únicamente que el encabezado de autenticación local debe recrearse.

## 4. Verificar entrega

Abrir MailHog:

```text
http://localhost:18025
```

El mensaje debe tener asunto `Tu horario Agosto-2026 fue publicado`.

## 5. Verificar persistencia y Outbox

```powershell
docker exec notification-recognition-postgres-1 psql `
  -U design_software_app `
  -d design-software-develop `
  -c "SELECT n.id, n.send_status, n.subject, n.body_summary, t.code AS template_code FROM notification.sent_notification n LEFT JOIN notification.notification_template t ON t.id = n.template_id WHERE n.source_event_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'::uuid; SELECT o.event_type, (o.published_at IS NOT NULL) AS publicado FROM notification.outbox o JOIN notification.sent_notification n ON o.payload->>'notification_id' = n.id::text WHERE n.source_event_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'::uuid;"
```

Son consultas `SELECT` y no modifican datos.

## 6. Verificar la traza

En Grafana Explore → Tempo → TraceQL:

```traceql
{ resource.service.name = "notification-worker" }
```

Buscar `amqp.consume scheduling.schedule.published` y abrir el Trace ID.

## Resultado esperado

- RabbitMQ: `routed: true`.
- MailHog: correo nuevo.
- PostgreSQL: `SENT` y plantilla aplicada.
- Outbox: `publicado = t`.
- Tempo: traza del worker con spans internos.

## Seguridad

- No publicar credenciales o DSN.
- No utilizar destinatarios reales.
- No detener, reiniciar o eliminar contenedores.
- No borrar mensajes o filas usados como evidencia.
