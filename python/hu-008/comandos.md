# Comandos — HU-008 Python

## Pruebas

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
& 'C:\Users\danna\AppData\Local\Temp\notification-py-runtime-20260825\Scripts\python.exe' -m pytest -q -p no:cacheprovider
```

## Arranque

Con DB/AMQP/SMTP preparados en variables de entorno:

```powershell
$env:PORT='38081'
python -m app.entrypoints.api

$env:WORKER_HEALTH_PORT='38082'
python -m app.entrypoints.worker
```

Publicar `scheduling.schedule.published` en el exchange `scheduling-events` y verificar MailHog, `sent_notification` y Outbox por `source_event_id`.
