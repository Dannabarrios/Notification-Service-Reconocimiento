# Comandos reproducibles — HU-005 Java

## Registro existente

```powershell
Invoke-WebRequest `
  -Uri 'http://localhost:28081/notifications/d1e6e51e-9fed-4f55-b799-be4945b589a1' `
  -Method Get -UseBasicParsing
```

## UUID válido pero inexistente

```powershell
try {
    Invoke-RestMethod `
      -Uri 'http://localhost:28081/notifications/99999999-9999-4999-8999-999999999995'
} catch {
    "HTTP $([int]$_.Exception.Response.StatusCode)"
    $_.ErrorDetails.Message
}
```

## Identificador inválido

```powershell
try {
    Invoke-RestMethod `
      -Uri 'http://localhost:28081/notifications/no-es-un-uuid-java'
} catch {
    "HTTP $([int]$_.Exception.Response.StatusCode)"
    $_.ErrorDetails.Message
}
```
