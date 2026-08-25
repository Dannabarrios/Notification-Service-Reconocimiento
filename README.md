# Notification Service — reconocimiento en Go, Java y Python

Repositorio personal de evidencias de lectura, ejecución y comprensión del microservicio `notification-service` en tres lenguajes. Cada implementación conserva el mismo contrato funcional y se estudia mediante las mismas ocho historias de usuario.

## Implementaciones

| Lenguaje | Código | Evidencias | Estado |
|---|---|---|---|
| Go | [Código](go/codigo/) | [HU-001 a HU-008](go/README.md) | Reconocimiento completo |
| Java / Spring Boot | [Código](java/codigo/) | [Índice Java](java/README.md) | Evidencias en preparación |
| Python / FastAPI | [Código](python/codigo/) | [Índice Python](python/README.md) | Evidencias en preparación |

## Regla de trabajo

Las implementaciones se ejecutan, estudian y documentan sin cambiar su comportamiento durante la actividad de reconocimiento. Las carpetas de HU contienen explicación, comandos, capturas, diagramas, resultados y mejoras propuestas.

No se incluyen credenciales, archivos `.env`, repositorios `.git` anidados, dependencias locales, cachés ni artefactos compilados.

## Historias de usuario comunes

1. Envío de notificación vía API.
2. Consumo de eventos AMQP.
3. Entrega por canales.
4. Resiliencia e idempotencia.
5. Consulta de notificaciones.
6. Plantillas.
7. Observabilidad.
8. Ejecución local end-to-end.

## Material común

- [Presentación](presentacion/README.md)

Los enlaces de video se incorporarán en las HU correspondientes cuando sean grabados y publicados.
