# Comandos — HU-001 Python

```powershell
$env:PORT='38081'
$env:NOTIFICATION_DB_DSN='<DSN construido desde el contenedor, sin publicarlo>'
& 'C:\Users\danna\AppData\Local\Temp\notification-py-runtime-20260825\Scripts\python.exe' -m app.entrypoints.api
```

```powershell
Invoke-RestMethod 'http://localhost:38081/health'
Invoke-RestMethod 'http://localhost:38081/ready' | ConvertTo-Json -Depth 5

$payload=@{recipient_id='10101010-1010-4010-8010-101010101001';recipient_email='hu001-python@example.com';channel='EMAIL';subject='Evidencia Python HU-001';source_service='recognition-python'}|ConvertTo-Json
Invoke-RestMethod 'http://localhost:38081/notifications' -Method Post -ContentType 'application/json' -Body $payload
```

Repetir con `channel='SMS'` dentro de `try/catch` para comprobar HTTP 400.
