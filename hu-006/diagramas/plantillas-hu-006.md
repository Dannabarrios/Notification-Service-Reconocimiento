# Diagramas — HU-006

## Plantilla existente y activa

```mermaid
flowchart TD
    A["Solicitud con template_code y variables"] --> B["TemplateRepository.FindByCode"]
    B --> C{"¿Existe y está activa?"}
    C -->|Sí| D["Render subject_template"]
    D --> E["Render body_template"]
    E --> F["Guardar subject, body_summary y template_id"]
    F --> G["Respuesta PENDING"]
```

## Fallback observado

```mermaid
flowchart TD
    A["Código de plantilla inexistente"] --> B["FindByCode devuelve nil"]
    B --> C["Conservar asunto explícito"]
    C --> D["body_summary = NULL"]
    D --> E["template_id = NULL"]
    E --> F["Solicitud aceptada sin advertencia"]
```

## Sustitución

```text
Tu horario {{schedule_name}} fue publicado
             + Agosto-2026
                         ↓
Tu horario Agosto-2026 fue publicado
```
