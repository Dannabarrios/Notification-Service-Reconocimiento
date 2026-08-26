# Comandos — HU-003 Python

Contar MailHog, crear `channel='IN_APP'`, consultar el UUID y volver a contar:

```powershell
$before=(Invoke-RestMethod 'http://localhost:18025/api/v2/messages?limit=1').total
$payload=@{recipient_id='40404040-4040-4040-8040-404040404003';recipient_email='hu003-python-inapp@example.com';channel='IN_APP';subject='Notificacion interna Python HU-003';source_service='recognition-python'}|ConvertTo-Json
$created=Invoke-RestMethod 'http://localhost:38081/notifications' -Method Post -ContentType 'application/json' -Body $payload
Invoke-RestMethod "http://localhost:38081/notifications/$($created.id)"
$after=(Invoke-RestMethod 'http://localhost:18025/api/v2/messages?limit=1').total
$after-$before
```
