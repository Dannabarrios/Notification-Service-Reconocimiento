# Comandos reproducibles — HU-006

## 1. Consultar plantillas activas

```powershell
docker exec notification-recognition-postgres-1 psql `
  -U design_software_app `
  -d design-software-develop `
  -c "SELECT code, channel, subject_template, body_template, is_active FROM notification.notification_template ORDER BY code;"
```

Es un `SELECT`; no modifica plantillas.

## 2. Crear una notificación con plantilla

```powershell
$templatePayload = @{
  recipient_id    = "66666666-1111-4666-8666-111111111111"
  recipient_email = "hu006-template@example.com"
  channel         = "EMAIL"
  subject         = "Asunto de respaldo HU-006"
  source_service  = "recognition-demo"
  template_code   = "SCHEDULE_PUBLISHED"
  template_vars   = @{
    schedule_name = "Agosto-2026"
    ficha         = "3145555"
  }
} | ConvertTo-Json -Depth 10

$templateResult = Invoke-RestMethod `
  -Uri "http://localhost:8080/notifications" `
  -Method Post `
  -ContentType "application/json" `
  -Body $templatePayload

$templateResult | ConvertTo-Json
```

El asunto de la plantilla reemplaza el asunto de respaldo.

## 3. Comprobar cuerpo y asociación

```powershell
docker exec notification-recognition-postgres-1 psql `
  -U design_software_app `
  -d design-software-develop `
  -c "SELECT n.id, n.subject, n.body_summary, n.send_status, t.code AS template_code, t.channel AS template_channel FROM notification.sent_notification n LEFT JOIN notification.notification_template t ON t.id = n.template_id WHERE n.id = '$($templateResult.id)'::uuid;"
```

El `LEFT JOIN` relaciona la notificación con la plantilla utilizada.

## 4. Probar una plantilla inexistente

```powershell
$fallbackPayload = @{
  recipient_id    = "66666666-2222-4666-8666-222222222222"
  recipient_email = "hu006-fallback@example.com"
  channel         = "EMAIL"
  subject         = "Asunto de respaldo por plantilla inexistente"
  source_service  = "recognition-demo"
  template_code   = "PLANTILLA_QUE_NO_EXISTE"
  template_vars   = @{
    schedule_name = "No debe utilizarse"
    ficha         = "3145555"
  }
} | ConvertTo-Json -Depth 10

$fallbackResult = Invoke-RestMethod `
  -Uri "http://localhost:8080/notifications" `
  -Method Post `
  -ContentType "application/json" `
  -Body $fallbackPayload

$fallbackResult | ConvertTo-Json
```

## 5. Comprobar el fallback

```powershell
docker exec notification-recognition-postgres-1 psql `
  -U design_software_app `
  -d design-software-develop `
  -c "SELECT id, subject, COALESCE(body_summary, 'NULL') AS body_summary, COALESCE(template_id::text, 'NULL') AS template_id FROM notification.sent_notification WHERE id = '$($fallbackResult.id)'::uuid;"
```

`COALESCE` cambia solamente la presentación de los valores nulos.

## 6. Ejecutar pruebas del renderizador

```powershell
Set-Location "C:\Users\danna\Downloads\notification-service-actividad\microservicio\design-software-notification-service-develop"
go test ./internal/domain/service -v
```

El resultado esperado es `PASS`.

## Seguridad

- Usar destinatarios y UUID ficticios.
- No modificar filas de plantillas.
- No mostrar credenciales ni DSN.
- No publicar plantillas con información personal.
