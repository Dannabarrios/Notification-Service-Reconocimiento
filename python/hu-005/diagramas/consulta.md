# Consulta Python

```mermaid
flowchart TD
    A[GET por id] --> B{UUID válido}
    B -->|No| C[400]
    B -->|Sí| D[Repository.find_by_id]
    D -->|Vacío| E[404]
    D -->|Existe| F[200]
```
