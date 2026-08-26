# Comandos — HU-006 Python

Enviar POST a `http://localhost:38081/notifications` con:

```powershell
$payload=@{recipient_id='60606060-6060-4060-8060-606060606006';recipient_email='hu006-python@example.com';channel='EMAIL';subject='Respaldo Python';source_service='recognition-python';template_code='SCHEDULE_PUBLISHED';template_vars=@{schedule_name='Python-Agosto-2026';ficha='3145555'}}|ConvertTo-Json -Depth 10
Invoke-RestMethod 'http://localhost:38081/notifications' -Method Post -ContentType 'application/json' -Body $payload
```

Repetir con `template_code='PLANTILLA_QUE_NO_EXISTE'`.
