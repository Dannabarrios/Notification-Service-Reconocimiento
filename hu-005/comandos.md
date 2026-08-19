# Comandos reproducibles — HU-005

Todas las operaciones son consultas HTTP y no modifican datos.

## 1. Consultar una notificación existente

```powershell
$foundResponse = Invoke-WebRequest `
  -Uri "http://localhost:8080/notifications/433b8e43-e959-401d-8079-64314d342505" `
  -Method Get `
  -UseBasicParsing

"HTTP status: $($foundResponse.StatusCode)"
$foundResponse.Content | ConvertFrom-Json | ConvertTo-Json
```

`Invoke-WebRequest` permite observar el código y el cuerpo. El resultado esperado es `200` con la notificación.

## 2. Consultar un UUID inexistente

```powershell
try {
  Invoke-RestMethod `
    -Uri "http://localhost:8080/notifications/99999999-9999-4999-8999-999999999999" `
    -Method Get
} catch {
  "HTTP status: $([int]$_.Exception.Response.StatusCode)"
  $_.ErrorDetails.Message
}
```

El UUID es válido, pero no corresponde a una fila. Se espera `404` y `NOT_FOUND`.

## 3. Consultar un identificador inválido

```powershell
try {
  Invoke-RestMethod `
    -Uri "http://localhost:8080/notifications/no-es-un-uuid" `
    -Method Get
} catch {
  "HTTP status: $([int]$_.Exception.Response.StatusCode)"
  $_.ErrorDetails.Message
}
```

El resultado esperado es `400 VALIDATION_ERROR`. No se consulta PostgreSQL.

## Qué explicar

1. `200` significa que el recurso existe y pudo leerse.
2. `404` significa que el formato es válido, pero no existe una notificación asociada.
3. `400` significa que la solicitud es inválida desde la entrada.
4. El endpoint no cambia el estado de la notificación.
5. La respuesta pública contiene un subconjunto de los campos persistidos.

## Seguridad

- Usar UUID y destinatarios ficticios.
- No mostrar credenciales ni cadenas DSN.
- No detener PostgreSQL para provocar un error.
