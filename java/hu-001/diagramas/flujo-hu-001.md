# Diagrama — HU-001 Java

```mermaid
sequenceDiagram
    actor Cliente
    participant HTTP as NotificationController
    participant UC as SendNotificationService
    participant PORT as NotificationRepository
    participant JDBC as JdbcNotificationRepository
    participant DB as PostgreSQL

    Cliente->>HTTP: POST /notifications (JSON)
    HTTP->>HTTP: Validar campos y canal

    alt solicitud inválida
        HTTP-->>Cliente: 400 VALIDATION_ERROR
    else solicitud válida
        HTTP->>UC: SendNotificationCommand
        UC->>UC: Crear notificación PENDING
        UC->>PORT: save(notification)
        PORT->>JDBC: Implementación del puerto
        JDBC->>DB: INSERT sent_notification
        DB-->>JDBC: Registro persistido
        JDBC-->>UC: SentNotification
        UC-->>HTTP: Resultado
        HTTP-->>Cliente: 202 Accepted + UUID
    end
```

## Lectura sencilla

1. El controlador recibe y valida el JSON.
2. Una solicitud inválida termina con `400` y no avanza al caso de uso.
3. Una solicitud válida se convierte en un comando.
4. El caso de uso crea la notificación con estado `PENDING`.
5. El repositorio JDBC la guarda en PostgreSQL.
6. La API devuelve `202` y el UUID para consultarla después.

## Patrón observado

```text
Adaptador de entrada        Núcleo de la aplicación       Adaptador de salida
Spring HTTP Controller  →   Caso de uso + dominio     →   JDBC + PostgreSQL
```

El caso de uso conoce el puerto `NotificationRepository`, pero no necesita conocer los detalles de PostgreSQL. Esa inversión de dependencias es una característica central de la arquitectura hexagonal.
