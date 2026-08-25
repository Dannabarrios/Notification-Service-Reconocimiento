# Auditoría HU — migración Python/FastAPI

Esta matriz separa lo que ya está comprobado por pruebas de lo que todavía exige evidencia en vivo.

| HU | Alcance | Estado Python | Evidencia actual | Pendiente para cerrar la HU |
|---|---|---|---|---|
| HU-001 | Envío vía API | Implementada | POST 202, PENDING y validaciones en pruebas | Captura/video del POST y consulta en BD. |
| HU-002 | Consumo de eventos AMQP | Implementada | Pruebas ACK/NACK, routing y eventos soportados | Video publicando evento y log del worker. |
| HU-003 | Entrega por canales | Implementada con límites del Go | Pruebas EMAIL/IN_APP y formato SMTP | Captura MailHog; explicar que IN_APP es no-op. |
| HU-004 | Resiliencia e idempotencia | Parcial por diseño original | `source_event_id`, `ON CONFLICT`, outbox y `SKIP LOCKED` | Demostrar evento repetido. No afirmar retries/DLQ porque no existen. |
| HU-005 | Consulta de notificación | Implementada | Pruebas GET 200, 404 y UUID inválido 400 | Capturas de los tres casos. |
| HU-006 | Plantillas | Implementada | Pruebas de render, fallback y variables | Captura de plantilla en BD y correo renderizado. |
| HU-007 | Observabilidad | Comprobada | Métricas HTTP/entrega en Prometheus y trazas API/worker en Tempo | Tomar capturas en Grafana y del log JSON en terminal. |
| HU-008 | Ejecución local end-to-end | Comprobada | Health/readiness, POST/GET, Rabbit, MailHog, SENT y outbox publicado | Grabar el mismo flujo para la evidencia audiovisual. |

## Resultado técnico

- Pruebas: 23/23 aprobadas, incluidas integraciones PostgreSQL.
- Ruff, compilación sintáctica e imports: aprobados.
- Base e infraestructura originales: mismo contenido después de normalizar finales de línea.
- Flujo end-to-end: aprobado contra la infraestructura Docker existente.
- Pendiente de entrega: capturas y video realizados por la aprendiz.

## Orden recomendado para la evidencia

1. Levantar únicamente la variante Python.
2. Ejecutar health/readiness y smoke test.
3. Publicar evento scheduling.
4. Capturar RabbitMQ, worker, MailHog, PostgreSQL/outbox y Grafana.
5. Repetir el mismo `event_id` para explicar idempotencia.
6. Terminar la grabación sin eliminar contenedores ni volúmenes.
