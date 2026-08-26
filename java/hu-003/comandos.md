# Comandos reproducibles — HU-003 Java

## 1. Contar mensajes de MailHog antes

```powershell
$before = (Invoke-RestMethod 'http://localhost:18025/api/v2/messages?limit=1').total
"Mensajes antes: $before"
```

## 2. Crear una notificación IN_APP

```powershell
$payload = @{
    recipient_id    = '44444444-4444-4444-8444-444444444403'
    recipient_email = 'hu003-java-inapp@example.com'
    channel         = 'IN_APP'
    subject         = 'Notificacion interna Java HU-003'
    source_service  = 'recognition-demo-java'
} | ConvertTo-Json

$response = Invoke-WebRequest `
    -Uri 'http://localhost:28081/notifications' `
    -Method Post `
    -ContentType 'application/json' `
    -Body $payload `
    -UseBasicParsing

"HTTP $($response.StatusCode)"
$notification = $response.Content | ConvertFrom-Json
$notification | ConvertTo-Json -Depth 5
```

## 3. Consultarla por UUID

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:28081/notifications/$($notification.id)" `
    -Method Get |
    ConvertTo-Json -Depth 5
```

## 4. Comprobar PostgreSQL

```powershell
docker exec notification-recognition-postgres-1 psql `
  -U design_software_app `
  -d design-software-develop `
  -c "SELECT id, recipient_email, channel, subject, send_status, created_at FROM notification.sent_notification WHERE id='d1e6e51e-9fed-4f55-b799-be4945b589a1'::uuid;"
```

## 5. Verificar que no apareció un correo

```powershell
$after = (Invoke-RestMethod 'http://localhost:18025/api/v2/messages?limit=1').total
"Mensajes despues: $after"
"Cambio: $($after - $before)"
```

El cambio esperado es `0` porque `IN_APP` no usa SMTP.
