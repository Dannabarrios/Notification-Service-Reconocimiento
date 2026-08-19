# Comandos reproducibles — HU-003

Estos comandos se ejecutaron en PowerShell y no modifican el código fuente.

## 1. Crear una notificación IN_APP

```powershell
$inAppPayload = @{
  recipient_id    = "77777777-7777-4777-8777-777777777777"
  recipient_email = "hu003-inapp@example.com"
  channel         = "IN_APP"
  subject         = "Notificacion interna HU-003"
  source_service  = "recognition-demo"
} | ConvertTo-Json

$inAppResult = Invoke-RestMethod `
  -Uri "http://localhost:8080/notifications" `
  -Method Post `
  -ContentType "application/json" `
  -Body $inAppPayload

$inAppResult | ConvertTo-Json
```

`$inAppPayload` contiene la solicitud. `$inAppResult` conserva la respuesta para reutilizar su UUID. El resultado observado fue `IN_APP` y `PENDING`.

## 2. Consultar la notificación por API

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/notifications/$($inAppResult.id)" `
  -Method Get | ConvertTo-Json
```

`$($inAppResult.id)` inserta en la URL el UUID devuelto por el POST.

## 3. Consultar PostgreSQL

```powershell
docker exec notification-recognition-postgres-1 psql `
  -U design_software_app `
  -d design-software-develop `
  -c "SELECT id, recipient_email, channel, subject, send_status, created_at FROM notification.sent_notification WHERE id = '433b8e43-e959-401d-8079-64314d342505'::uuid;"
```

La operación es un `SELECT`; no cambia información. Confirma canal `IN_APP` y estado `PENDING`.

## 4. Verificar que IN_APP no genera correo

Abrir o actualizar:

```text
http://localhost:18025
```

MailHog permaneció en `Inbox (2)`, con los dos correos EMAIL anteriores. No apareció un mensaje nuevo por `IN_APP`.

## Qué explicar

1. `CompositeNotifier` conoce ambos canales.
2. `EMAIL` usa un efecto externo mediante SMTP.
3. `IN_APP` no usa SMTP; depende de persistencia y consulta.
4. El flujo HTTP probado conserva `IN_APP` como `PENDING`.
5. El worker AMQP actual fija sus notificaciones en `EMAIL`.

## Seguridad

- Utilizar UUID y correos ficticios.
- No mostrar DSN, contraseñas o tokens.
- No eliminar mensajes de MailHog durante la evidencia.
