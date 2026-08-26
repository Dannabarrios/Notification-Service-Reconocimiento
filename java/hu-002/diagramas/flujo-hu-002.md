# Diagrama — HU-002 Java

```mermaid
sequenceDiagram
    actor Productor
    participant EX as Exchange monitoring-events
    participant Q as Cola notification-service.events
    participant C as DomainEventConsumer
    participant UC as ConsumeDomainEventService
    participant SMTP as MailHog SMTP
    participant DB as PostgreSQL
    participant O as OutboxRelay

    Productor->>EX: monitoring.alert.triggered
    EX->>Q: Enrutar por routing key
    Q->>C: Entregar mensaje
    C->>C: Deserializar y validar sobre

    alt sobre inválido
        C-->>Q: NACK, requeue=false
    else sobre válido
        C->>UC: handle(command)
        UC->>SMTP: Enviar correo
        SMTP-->>UC: Entrega local aceptada
        UC->>DB: Guardar SENT + Outbox
        DB-->>UC: Transacción confirmada
        C-->>Q: ACK
        O->>DB: Leer evento pendiente
        O->>EX: Publicar notification.notification.sent
        O->>DB: Marcar published_at
    end
```

## Ideas clave

- El exchange enruta; no almacena el mensaje como una cola.
- La cola desacopla al productor del worker.
- El ACK se realiza después de ejecutar el caso de uso.
- Notificación y Outbox se guardan juntas para evitar perder el evento de salida.
- MailHog reemplaza un servidor de correo real durante la demostración local.
