# Comandos reproducibles — HU-004

Los comandos se ejecutaron en PowerShell sin modificar código, configuración o contenedores.

## 1. Publicar un cuerpo que no es JSON

```powershell
$invalidMessageRequest = @{
  properties       = @{ content_type = "application/json" }
  routing_key      = "monitoring.alert.triggered"
  payload          = "esto-no-es-json"
  payload_encoding = "string"
} | ConvertTo-Json -Depth 10

Invoke-RestMethod `
  -Uri "http://localhost:15672/api/exchanges/%2F/monitoring-events/publish" `
  -Method Post `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $invalidMessageRequest | ConvertTo-Json
```

`routed: true` solo confirma el encaminamiento. El worker no puede deserializar el contenido y ejecuta `NACK` sin requeue.

## 2. Publicar un evento no soportado

```powershell
$unsupportedEvent = @{
  event_id       = "88888888-8888-4888-8888-888888888888"
  event_type     = "monitoring.event.unsupported"
  source_service = "recognition-demo"
  timestamp      = (Get-Date).ToUniversalTime().ToString("o")
  version        = "1.0"
  payload        = @{}
} | ConvertTo-Json -Depth 10 -Compress

$unsupportedRequest = @{
  properties       = @{ content_type = "application/json" }
  routing_key      = "monitoring.alert.triggered"
  payload          = $unsupportedEvent
  payload_encoding = "string"
} | ConvertTo-Json -Depth 10

Invoke-RestMethod `
  -Uri "http://localhost:15672/api/exchanges/%2F/monitoring-events/publish" `
  -Method Post `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $unsupportedRequest | ConvertTo-Json
```

El envelope sí es JSON, pero el caso de uso no reconoce su `event_type`. El consumidor registra el error y confirma el mensaje.

## 3. Verificar la cola

Abrir:

```text
http://localhost:15672/#/queues
```

El resultado observado fue una sola cola, con `Ready`, `Unacked` y `Total` en cero. No existió una DLQ.

## 4. Idempotencia

La publicación repetida y las consultas utilizadas están documentadas en [comandos de HU-002](../hu-002/comandos.md). La prueba conserva una fila por `source_event_id`, pero genera dos entregas SMTP.

## Qué explicar

1. `routed` no significa procesado correctamente.
2. `NACK` sin requeue descarta el mensaje cuando no hay DLQ.
3. Un `ACK` después de un error impide el reintento.
4. El worker permanece vivo, pero puede perder mensajes.
5. La idempotencia debe cubrir también el efecto externo.

## Seguridad

- Usar identificadores y payloads ficticios.
- No mostrar credenciales ni DSN.
- No pulsar `Delete`, `Purge` o `Unbind`.
- No detener contenedores para simular fallos.
