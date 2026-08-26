# Diagrama — selección de canales en Java

```mermaid
flowchart TD
    A[Notificación] --> B{CompositeNotifier}
    B -->|EMAIL| C[SmtpNotifier]
    C --> D[SMTP localhost:1025]
    D --> E[MailHog]
    B -->|IN_APP| F[InAppNotifier]
    F --> G[Sin efecto externo actual]
    G --> H[Registro consultable en PostgreSQL]
```

## Diferencia entre creación y entrega

```text
POST /notifications
    └─ valida y persiste PENDING
       └─ no llama a CompositeNotifier

Evento AMQP
    └─ worker llama a CompositeNotifier
       └─ actualmente crea siempre canal EMAIL
```

Los adaptadores soportan ambos canales, pero el flujo completo `IN_APP → SENT` todavía no está conectado en la implementación original.
