# Flujo de la HU-001

```mermaid
sequenceDiagram
    actor Cliente
    participant HTTP as Adaptador HTTP
    participant UC as SendNotification
    participant Repo as Repositorio PostgreSQL
    participant DB as notification.sent_notification

    Cliente->>HTTP: POST /notifications
    HTTP->>HTTP: Decodificar y validar contrato
    alt Solicitud inválida
        HTTP-->>Cliente: 400 VALIDATION_ERROR
    else Solicitud válida
        HTTP->>UC: SendNotificationCommand
        UC->>UC: Crear notificación PENDING
        UC->>Repo: Save(notification)
        Repo->>DB: INSERT
        DB-->>Repo: UUID y created_at
        Repo-->>UC: Notificación persistida
        UC-->>HTTP: Resultado
        HTTP-->>Cliente: 202 Accepted
    end
```

## Lectura del diagrama

La validación ocurre en el adaptador de entrada. El caso de uso no conoce HTTP y el dominio no depende de PostgreSQL. Esta separación refleja la arquitectura hexagonal del microservicio.

