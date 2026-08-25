# Notification Service - migracion Go a Java

Este entregable reimplementa en Java/Spring Boot el microservicio de notificaciones que estaba desarrollado en Go, conservando el contrato HTTP, el esquema PostgreSQL, la topologia RabbitMQ, SMTP/MailHog, el patron outbox y la infraestructura Docker suministrada.

## Estructura

- `notification-service/`: implementacion Java 21 + Spring Boot 4.1.
- `design-software-notification-db/`: migraciones Liquibase originales, sin cambios de modelo.
- `docker-infra/`: infraestructura Docker original mas `docker-compose.notification.yml`, que integra el servicio Java.
- `scripts/`: pruebas manuales de demostracion en PowerShell.
- `MIGRATION_PARITY.md`: matriz de equivalencia Go -> Java.
- `VERIFICATION.md`: evidencia de validacion y pasos finales de demostracion.

## Arquitectura de ejecucion

La misma imagen Java se ejecuta con dos perfiles, igual que los dos binarios del servicio Go:

- `notification-api` (`SPRING_PROFILES_ACTIVE=api`) -> HTTP en `8080`.
- `notification-worker` (`SPRING_PROFILES_ACTIVE=worker`) -> consumidor RabbitMQ + relay outbox + health HTTP en `8081`.

No se modificaron tablas, columnas, relaciones, constraints, indices ni datos seed de `design-software-notification-db`.

## Demostracion completa con Docker

Desde PowerShell:

```powershell
cd docker-infra
Copy-Item .env.notification.example .env.notification
```

Revise las credenciales de `.env.notification` si desea cambiarlas y ejecute:

```powershell
docker compose --env-file .env.notification `
  -f docker-compose.yml `
  -f docker-compose.notification.yml `
  --profile broker `
  --profile notification `
  --profile observability `
  up -d --build
```

El overlay realiza en orden:

1. Levanta PostgreSQL.
2. Ejecuta las migraciones Liquibase del dominio `notification`.
3. Crea/actualiza el usuario `design_software_app` y le concede `notification_writer`.
4. Levanta RabbitMQ y MailHog.
5. Construye la imagen Java.
6. Inicia `notification-api` y `notification-worker`.
7. Conecta trazas/metricas al collector OpenTelemetry existente.

Verifique contenedores:

```powershell
docker compose --env-file .env.notification `
  -f docker-compose.yml `
  -f docker-compose.notification.yml `
  --profile broker --profile notification --profile observability ps
```

## Smoke test del API

Desde la raiz del entregable:

```powershell
.\scripts\smoke-test.ps1
```

Prueba:

- `GET http://localhost:28081/health`
- `GET http://localhost:28081/ready`
- `POST http://localhost:28081/notifications`
- `GET http://localhost:28081/notifications/{id}`

El POST manual conserva el comportamiento Go: crea una notificacion `PENDING`; no envia correo directamente.

## Demostracion del worker + RabbitMQ + SMTP + outbox

Con los contenedores activos:

```powershell
.\scripts\publish-demo-event.ps1
```

El script publica `scheduling.schedule.published`. El worker debe:

1. Consumir el evento.
2. Resolver el destinatario stub.
3. Renderizar `SCHEDULE_PUBLISHED` desde PostgreSQL.
4. Enviar el email por MailHog.
5. Persistir `sent_notification` con `SENT`.
6. Insertar `notification.notification.sent` en `notification.outbox` en la misma transaccion.
7. Publicar el outbox en `notification-events` y marcar `published_at`.

MailHog: `http://localhost:28025`.

RabbitMQ Management: `http://localhost:25673`.

Grafana: `http://localhost:23000`.

## Consultar datos de demostracion

```powershell
cd docker-infra
docker compose --env-file .env.notification -f docker-compose.yml -f docker-compose.notification.yml exec postgres `
  psql -U design_software_user -d design-software-develop -c "TABLE notification.sent_notification;"

docker compose --env-file .env.notification -f docker-compose.yml -f docker-compose.notification.yml exec postgres `
  psql -U design_software_user -d design-software-develop -c "TABLE notification.outbox;"
```

## Pruebas Java

En `notification-service/`:

```powershell
.\mvnw.cmd test
```

Build local:

```powershell
.\mvnw.cmd clean package
```

## Cuidado con el entorno compartido

Este entregable no incluye instrucciones para eliminar contenedores ni volúmenes. Antes de administrar el ciclo de vida de Docker, confirme qué proyecto está activo y conserve los datos del laboratorio. Para la demostración basta con iniciar y verificar los servicios indicados.
