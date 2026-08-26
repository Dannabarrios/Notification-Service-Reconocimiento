# Comandos reproducibles — HU-006 Java

## Consultar plantillas

```powershell
docker exec notification-recognition-postgres-1 psql `
  -U design_software_app -d design-software-develop `
  -c "SELECT code, channel, subject_template, body_template, is_active FROM notification.notification_template ORDER BY code;"
```

## Plantilla existente

```powershell
$payload = @{
    recipient_id    = '66666666-6666-4666-8666-666666666406'
    recipient_email = 'hu006-java-template@example.com'
    channel         = 'EMAIL'
    subject         = 'Asunto de respaldo Java HU-006'
    source_service  = 'recognition-demo-java'
    template_code   = 'SCHEDULE_PUBLISHED'
    template_vars   = @{
        schedule_name = 'Java-Agosto-2026'
        ficha         = '3145555'
    }
} | ConvertTo-Json -Depth 10

Invoke-RestMethod -Uri 'http://localhost:28081/notifications' `
  -Method Post -ContentType 'application/json' -Body $payload
```

## Plantilla inexistente

```powershell
$payload = @{
    recipient_id    = '66666666-6666-4666-8666-666666666407'
    recipient_email = 'hu006-java-fallback@example.com'
    channel         = 'EMAIL'
    subject         = 'Asunto de respaldo por plantilla inexistente Java'
    source_service  = 'recognition-demo-java'
    template_code   = 'PLANTILLA_QUE_NO_EXISTE'
    template_vars   = @{ schedule_name = 'No debe utilizarse'; ficha = '3145555' }
} | ConvertTo-Json -Depth 10

Invoke-RestMethod -Uri 'http://localhost:28081/notifications' `
  -Method Post -ContentType 'application/json' -Body $payload
```
