# Comandos reproducibles — HU-001

Esta guía permite repetir la demostración desde PowerShell. Los comandos no modifican el código fuente.

## 1. Ubicarse en el microservicio

```powershell
cd "C:\Users\danna\Downloads\notification-service-actividad\microservicio\design-software-notification-service-develop"
```

`cd` significa **change directory**. Cambia la carpeta actual de PowerShell al microservicio.

## 2. Configurar la conexión local

Las variables de entorno se configuran únicamente en la ventana actual de PowerShell. La contraseña no debe aparecer en capturas, documentos ni videos.

Variables requeridas:

```text
NOTIFICATION_DB_DSN                 conexión con PostgreSQL
NOTIFICATION_DEPLOYMENT_ENVIRONMENT nombre del ambiente
OTEL_EXPORTER_OTLP_ENDPOINT         destino de OpenTelemetry
OTEL_EXPORTER_OTLP_INSECURE         conexión local sin TLS
PORT                               puerto de la API
```

Para la demostración se deben cargar las variables localmente antes de iniciar la API. No se publica el valor de `NOTIFICATION_DB_DSN` porque contiene una credencial.

## 3. Iniciar la API

```powershell
go run ./cmd/notification-api
```

Significado:

- `go run`: compila temporalmente y ejecuta un programa Go.
- `./cmd/notification-api`: carpeta que contiene el punto de entrada de la API.

Resultado esperado:

```text
notification-api listening on :8080
```

La ventana queda ocupada mientras la API está funcionando. No debe cerrarse durante la demostración.

**Captura 1:** terminal mostrando únicamente `notification-api listening on :8080`.

## 4. Abrir otra ventana de PowerShell

Los siguientes comandos se ejecutan en una segunda ventana porque la primera mantiene la API activa.

### Comprobar liveness

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/health" -Method Get
```

Resultado esperado:

```text
status
------
ok
```

`/health` confirma que el proceso está vivo.

### Comprobar readiness

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/ready" -Method Get | ConvertTo-Json -Depth 5
```

Resultado esperado:

```json
{
  "checks": [
    {
      "name": "database",
      "ok": true
    }
  ],
  "status": "ok"
}
```

`/ready` confirma que la API también puede conectarse con PostgreSQL.

**Captura 2:** respuestas de `/health` y `/ready` en la segunda ventana.

## 5. Enviar una notificación válida

```powershell
$payload = @{
  recipient_id    = "11111111-1111-4111-8111-111111111111"
  recipient_email = "hu001-demo@example.com"
  channel         = "EMAIL"
  subject         = "Demostracion HU-001"
  source_service  = "recognition-demo"
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri "http://localhost:8080/notifications" `
  -Method Post `
  -ContentType "application/json" `
  -Body $payload | ConvertTo-Json
```

Explicación:

- `$payload`: variable que contiene los datos de prueba.
- `ConvertTo-Json`: transforma el objeto de PowerShell en JSON.
- `Invoke-RestMethod`: realiza la petición HTTP.
- `-Method Post`: indica que se enviarán datos.
- `-ContentType`: declara que el cuerpo está en formato JSON.
- `-Body $payload`: envía el JSON creado anteriormente.

Resultado esperado:

- Un UUID generado.
- Canal `EMAIL`.
- Estado `PENDING`.
- Asunto `Demostracion HU-001`.

**Captura 3:** comando y respuesta de la solicitud válida. No debe aparecer ninguna variable de conexión.

## 6. Demostrar una validación negativa

```powershell
$invalidPayload = @{
  recipient_id    = "22222222-2222-4222-8222-222222222222"
  recipient_email = "invalid@example.com"
  channel         = "SMS"
  subject         = "Solicitud invalida HU-001"
} | ConvertTo-Json

try {
  Invoke-RestMethod `
    -Uri "http://localhost:8080/notifications" `
    -Method Post `
    -ContentType "application/json" `
    -Body $invalidPayload
} catch {
  $_.ErrorDetails.Message
}
```

Resultado esperado:

```json
{
  "error_code": "VALIDATION_ERROR",
  "message": "channel debe ser EMAIL o IN_APP"
}
```

**Captura 4:** respuesta de validación para el canal `SMS`.

## 7. Qué explicar durante la demo

1. `health` verifica que el proceso esté vivo.
2. `ready` verifica que PostgreSQL esté disponible.
3. El POST válido se acepta y se guarda con estado `PENDING`.
4. `PENDING` no significa que el correo ya haya sido enviado.
5. El canal `SMS` se rechaza porque el contrato solo permite `EMAIL` e `IN_APP`.

## Seguridad de las capturas

Antes de guardar una captura, revisar que no aparezcan:

- `NOTIFICATION_DB_DSN`.
- Contraseñas.
- Tokens.
- Correos personales.
- Información de otros repositorios o contenedores.

