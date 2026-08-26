# Diagrama — observabilidad Java

```mermaid
flowchart LR
    API[notification-api] -->|OTLP métricas y trazas| COL[OpenTelemetry Collector]
    WORKER[notification-worker] -->|OTLP métricas y trazas| COL
    COL --> PROM[Prometheus]
    COL --> TEMPO[Tempo]
    PROM --> G[Grafana]
    TEMPO --> G
    API -. logs locales .-> LOG[Archivos stdout]
    WORKER -. logs locales .-> LOG
    COL -. logs no observados .-> LOKI[Loki]
    LOKI --> G
```

## Lectura

- La línea continua representa señales verificadas.
- La línea punteada hacia Loki representa el vacío encontrado.
- Grafana funciona como interfaz de consulta; no almacena por sí mismo las señales.
