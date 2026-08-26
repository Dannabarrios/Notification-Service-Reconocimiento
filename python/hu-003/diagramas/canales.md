# Canales Python

```mermaid
flowchart TD
    A[CompositeNotifier] -->|EMAIL| B[SMTPNotifier]
    B --> C[MailHog]
    A -->|IN_APP| D[InAppNotifier]
    D --> E[Sin efecto externo actual]
```
