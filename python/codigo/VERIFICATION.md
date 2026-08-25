# Verificación del entregable Python/FastAPI

Fecha de revisión: 2026-08-25.

## Resultado comprobado

| Verificación | Resultado | Evidencia |
|---|---|---|
| Suite Python | OK | 23 pruebas aprobadas, incluidas las 2 de integración PostgreSQL. |
| Ruff | OK | `ruff check .` sin hallazgos después del formateo. |
| Sintaxis | OK | `python -m compileall -q app tests`. |
| Composition roots | OK | Imports de `app.entrypoints.api` y `app.entrypoints.worker`. |
| API HTTP | OK en pruebas | POST 202, validaciones, GET 200/404/400, health y readiness. |
| Worker y AMQP | OK en pruebas | ACK/NACK, eventos soportados/no soportados y propagación `traceparent`. |
| Negocio | OK en pruebas | Plantillas/fallback, SENT/FAILED, canales, SMTP y outbox. |
| Base PostgreSQL/Liquibase | OK | 46 archivos presentes y sin diferencias de contenido después de normalizar CRLF/LF. Los hashes de bytes difieren por el fin de línea. |
| Infraestructura Docker base | OK | 14/14 archivos originales presentes y sin diferencias de contenido después de normalizar CRLF/LF. Solo se añadieron el overlay y documentación. |
| End-to-end local + Docker | OK | API y worker Python locales contra PostgreSQL, RabbitMQ, MailHog, OTel, Prometheus y Tempo existentes. |
| Compose aislado | CONFIGURACIÓN VÁLIDA | `docker compose ... config --quiet` finalizó con código 0. No se creó un segundo conjunto de contenedores. |
| Observabilidad | OK | Métricas HTTP y `notification_delivered` en Prometheus; trazas API/worker en Tempo. |

## Cobertura de las 23 pruebas aprobadas

- POST `/notifications` y validaciones.
- GET existente, inexistente y UUID inválido.
- `/health` y `/ready`.
- Render de plantillas y fallback.
- Evento monitoring: `SENT` más outbox.
- Fallo notifier: `FAILED` sin outbox.
- Evento scheduling y resolución de destinatario.
- Evento no soportado.
- Envelope válido: ACK aunque falle el caso de uso.
- Envelope inválido: NACK sin requeue.
- Propagación W3C `traceparent`.
- Dispatch EMAIL/IN_APP.
- Formato SMTP equivalente al Go.

Las 2 pruebas de integración se ejecutaron con la PostgreSQL del laboratorio y fueron aprobadas.

## Entorno de verificación

El proyecto declara Python 3.13 y sus Dockerfiles usan Python 3.13. En esta revisión local se utilizó Python 3.12.13 para las pruebas, porque era el intérprete disponible; las dependencias se instalaron correctamente en un entorno virtual de ruta corta para evitar el límite de rutas de Windows.

## Validación en vivo ejecutada

Resultado del evento `2d7d7235-9184-453f-94b0-5a556330b921`:

- API y worker: `health=ok`, `ready=ok`.
- POST HTTP: `PENDING`; GET devolvió el mismo ID.
- Dos publicaciones RabbitMQ: ambas enrutadas.
- PostgreSQL: una sola fila `SENT`.
- Outbox: publicado.
- MailHog: dos correos para el evento duplicado.
- Prometheus: métricas HTTP y `notification_delivered{channel="EMAIL",status="SENT"}=2`.
- Tempo: trazas recientes de API y worker.

Esto confirma la misma limitación del Go y Java: una fila idempotente no impide repetir el correo.

## Repetición con el Compose aislado

Con Docker Desktop disponible, ejecutar el compose descrito en el README y después:

```powershell
.\scripts\smoke-test.ps1
.\scripts\publish-demo-event.ps1
```

Se debe comprobar API/worker, RabbitMQ, MailHog, PostgreSQL, outbox y observabilidad. Los overlays ahora usan nombres de proyecto y puertos distintos para no interferir con el laboratorio existente.

## Límites heredados del Go

- No existen retries, backoff ni DLQ.
- El worker usa EMAIL para los eventos soportados.
- IN_APP es un adaptador no-op.
- El POST HTTP persiste `PENDING`; no entrega directamente.
- La idempotencia de persistencia no equivale a entrega SMTP exactamente una vez.
