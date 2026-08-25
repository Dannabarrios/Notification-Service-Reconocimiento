# Verificación del entregable Java

Fecha de revisión: 2026-08-25.

## Resultado comprobado

La migración Java fue contrastada con el microservicio Go suministrado. La copia de trabajo compila, ejecuta sus pruebas, genera el paquete Spring Boot y completó un flujo end-to-end contra la infraestructura Docker existente.

| Verificación | Resultado | Evidencia |
|---|---|---|
| Compilación y empaquetado Maven | OK | `mvn clean package` finalizó con código 0 usando JDK 24 y destino Java 21. |
| Suite Java | OK | 15 pruebas, 0 fallos, 0 errores y 0 omitidas. |
| API HTTP | OK en pruebas | POST 202, validaciones, GET 200, GET 404 y UUID inválido 400. |
| Consumo AMQP | OK en pruebas | Envelope inválido: NACK sin requeue. Envelope válido: ACK incluso si falla el caso de uso, igual al Go. |
| Canales | OK en pruebas | `CompositeNotifier` despacha EMAIL e IN_APP y registra métricas. |
| Casos de uso y plantillas | OK en pruebas | PENDING, SENT/FAILED, outbox y sustitución de variables. |
| Base PostgreSQL/Liquibase | OK | 46 archivos presentes y sin diferencias de contenido después de normalizar CRLF/LF. Los bytes no son idénticos porque cambió el fin de línea. |
| Infraestructura Docker base | OK | 14/14 archivos originales presentes y sin diferencias de contenido después de normalizar CRLF/LF. Se añadieron 4 archivos de integración/documentación. |
| End-to-end local + Docker | OK | API y worker Java locales contra PostgreSQL, RabbitMQ, MailHog, OTel, Prometheus y Tempo existentes. |
| Compose aislado | CONFIGURACIÓN VÁLIDA | `docker compose ... config --quiet` finalizó con código 0. No se creó un segundo conjunto de contenedores. |
| Observabilidad | OK | 40 series HTTP observadas en Prometheus y 4 trazas recientes de API en Tempo. |

## Pruebas ejecutadas

- `DomainEventConsumerTest`: 2.
- `NotificationControllerTest`: 6.
- `CompositeNotifierTest`: 2.
- `ConsumeDomainEventServiceTest`: 2.
- `SendNotificationServiceTest`: 2.
- `TemplateRendererTest`: 1.

Total: **15 pruebas aprobadas**.

Durante la revisión se corrigió un error de tipado en `OutboxRelay` que impedía compilar con la API actual de Spring AMQP. Después de la corrección, `clean package` terminó correctamente.

## Validación en vivo ejecutada

Resultado del evento `56066ee9-7f71-4cb7-8a22-bda68b22deb3`:

- API y worker: `health=ok`, `ready=ok`.
- POST HTTP: `PENDING`; GET devolvió el mismo ID.
- Dos publicaciones RabbitMQ: ambas enrutadas.
- PostgreSQL: una sola fila `SENT`.
- Outbox: `notification.notification.sent` publicado.
- MailHog: dos correos para el evento duplicado.

La última observación confirma la limitación heredada: la idempotencia está en persistencia, no en el efecto SMTP.

## Repetición con el Compose aislado

Con Docker Desktop disponible, ejecutar desde `docker-infra`:

```powershell
Copy-Item .env.notification.example .env.notification

docker compose --env-file .env.notification `
  -f docker-compose.yml `
  -f docker-compose.notification.yml `
  --profile broker `
  --profile notification `
  --profile observability `
  up -d --build
```

Luego, desde la raíz del entregable:

```powershell
.\scripts\smoke-test.ps1
.\scripts\publish-demo-event.ps1
```

La repetición queda aprobada cuando:

1. API `/health` y `/ready` responden correctamente.
2. El worker `/ready` confirma base de datos y broker.
3. POST crea una notificación `PENDING` y GET permite consultarla.
4. El worker consume un evento RabbitMQ.
5. MailHog recibe el correo.
6. PostgreSQL registra `SENT` y el outbox publicado.
7. Grafana permite observar métricas o trazas del flujo.

## Límites heredados del Go

- No existen retries, backoff ni DLQ.
- El worker usa EMAIL para los eventos soportados.
- IN_APP es un adaptador no-op.
- La idempotencia evita duplicar la fila por `source_event_id`, pero no garantiza exactamente una entrega SMTP si el envío ocurre antes de detectar el conflicto.
- El POST HTTP persiste `PENDING`; no entrega directamente.
