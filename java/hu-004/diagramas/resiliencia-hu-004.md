# Diagrama — resiliencia e idempotencia Java

```mermaid
flowchart TD
    A[Mensaje RabbitMQ] --> B{Sobre JSON válido}
    B -->|No| C[NACK sin requeue]
    C --> D[Mensaje descartado]
    B -->|Sí| E[Ejecutar caso de uso]
    E --> F{Procesamiento correcto}
    F -->|No| G[Registrar error]
    G --> H[ACK sin reintento]
    F -->|Sí| I[Enviar por SMTP]
    I --> J[INSERT por source_event_id]
    J --> K{Ya existía}
    K -->|Sí| L[No duplica BD ni Outbox]
    K -->|No| M[Guardar notificación + Outbox]
```

## Límite actual

```text
Evento repetido
   ├─ SMTP ocurre primero       → correo duplicado
   └─ restricción única después → una sola fila
```

## Flujo recomendado

```mermaid
flowchart LR
    A[Mensaje] --> B[Reservar event_id]
    B -->|Duplicado| C[ACK sin reenviar]
    B -->|Nuevo| D[Intentar entrega]
    D -->|Fallo transitorio| E[Retry con backoff]
    E -->|Agota intentos| F[DLQ]
    D -->|Éxito| G[Guardar resultado + Outbox]
    G --> H[ACK]
```
