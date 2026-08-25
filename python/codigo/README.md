# notification-service — migración Go → Python/FastAPI

Este entregable reimplementa el microservicio original de notificaciones en **Python 3.13 + FastAPI**, tomando la implementación Go como contrato de comportamiento.

Se conserva:

- La base de datos PostgreSQL y todos los changelogs Liquibase **sin cambios**.
- La separación entre proceso HTTP (`notification-api`) y proceso worker (`notification-worker`).
- Los endpoints, códigos HTTP, estructuras JSON y reglas de validación principales.
- RabbitMQ, MailHog, outbox transaccional, idempotencia y propagación W3C `traceparent`.
- La infraestructura Docker base. Se agregó únicamente `docker-compose.notification.yml` como overlay para ejecutar la versión FastAPI.

## Estructura

```text
notification-service-fastapi-migrated/
├── design-software-notification-db/   # misma BD/Liquibase del Go
├── docker-infra/                      # infraestructura original
│   └── docker-compose.notification.yml# overlay FastAPI agregado
├── notification-service/              # nuevo microservicio Python
│   ├── app/
│   │   ├── domain/
│   │   ├── application/
│   │   ├── adapters/
│   │   ├── platform/
│   │   └── entrypoints/
│   ├── deploy/
│   ├── scripts/
│   └── tests/
├── MIGRATION_PARITY.md
├── REVIEW.md
└── VERIFICATION.md
```

## Contrato HTTP preservado

| Método | Endpoint | Comportamiento |
|---|---|---|
| `GET` | `/health` | Liveness, `200 {"status":"ok"}` |
| `GET` | `/ready` | Readiness de dependencias; `200` o `503` |
| `POST` | `/notifications` | Valida y persiste una notificación en estado `PENDING`; responde `202` |
| `GET` | `/notifications/{id}` | Devuelve la notificación; `404` si no existe |

Canales aceptados: `EMAIL`, `IN_APP`.

## Worker preservado

Consume desde RabbitMQ:

- exchange `scheduling-events`, routing key `scheduling.schedule.published`;
- exchange `monitoring-events`, routing key `monitoring.alert.triggered`;
- cola `notification-service.events`.

Cuando la entrega es exitosa, persiste `SENT` y crea en la misma transacción un evento de outbox `notification.notification.sent`. El relay publica esos eventos en `notification-events` y marca `published_at`.

### Comportamientos deliberadamente iguales al Go

- Un `scheduling.schedule.published` notifica únicamente a `published_by`; no hace fan-out sobre `instructor_ids`.
- El worker genera notificaciones `EMAIL` para los eventos soportados, incluso si la plantilla seed `ALERT_TRIGGERED` está marcada `IN_APP`; así funciona el Go actual.
- Si falla SMTP, se persiste `FAILED` y no se genera outbox.
- Si una plantilla no existe, está inactiva o falla su consulta, se conserva el asunto fallback.
- El envío SMTP usa el `subject` como asunto y también como cuerpo, igual que la implementación Go original.
- No se agregaron retries, backoff, DLQ ni integración real con actors-service.

# Ejecución con Docker

La variante Python usa el proyecto Compose aislado `notification-python` y puertos propios para no tocar ni reemplazar los contenedores del laboratorio Go o de Java.

## 1. Preparar variables

```powershell
cd docker-infra
Copy-Item .env.notification.example .env.notification
```

## 2. Construir y levantar el entorno

```powershell
docker compose --env-file .env.notification `
  -f docker-compose.yml `
  -f docker-compose.notification.yml `
  --profile broker `
  --profile notification `
  --profile observability `
  up -d --build
```

El overlay espera la salud de PostgreSQL, ejecuta Liquibase, crea/configura el usuario de aplicación y solo después inicia la API y el worker.

Puertos:

- API: `http://localhost:38081`
- Worker health: `http://localhost:38082`
- RabbitMQ Management: `http://localhost:35673`
- MailHog: `http://localhost:38025`
- Grafana: `http://localhost:33000`

## 3. Validar la demostración

Desde la carpeta `notification-service`:

```powershell
.\scripts\smoke-test.ps1
.\scripts\publish-demo-event.ps1
```

Después del segundo comando, revisar MailHog y los logs:

```powershell
cd ..\docker-infra
docker compose --env-file .env.notification `
  -f docker-compose.yml `
  -f docker-compose.notification.yml `
  logs notification-api notification-worker
```

# Ejecución local sin Docker para el código Python

Requiere PostgreSQL/RabbitMQ/MailHog accesibles y variables equivalentes a las del Go:

```powershell
cd notification-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements-dev.txt

$env:NOTIFICATION_DB_DSN="postgres://design_software_app:change-me-app@localhost:15432/design-software-develop?sslmode=disable"
python -m app.entrypoints.api
```

En Windows, si la ruta del proyecto es muy larga, cree el entorno virtual en una ruta corta (por ejemplo `C:\venvs\notification-python`) o utilice Docker. Esto evita errores al instalar librerías nativas como `psycopg`.

Para el worker, en otra consola:

```powershell
$env:NOTIFICATION_DB_DSN="postgres://design_software_app:change-me-app@localhost:15432/design-software-develop?sslmode=disable"
$env:NOTIFICATION_AMQP_URL="amqp://app:app@localhost:5672/"
$env:NOTIFICATION_SMTP_ADDR="localhost:1025"
python -m app.entrypoints.worker
```

# Pruebas

```powershell
cd notification-service
pytest -q
```

Las pruebas de integración se ejecutan si `NOTIFICATION_DB_DSN` está configurado. Ver `VERIFICATION.md` para la evidencia de esta entrega.
