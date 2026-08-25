# Comandos reproducibles — HU-001 Java

Estos comandos son de reconocimiento. No cambian el código ni detienen o eliminan contenedores.

## 1. Ubicarse en el microservicio Java

```powershell
Set-Location "C:\Users\danna\Downloads\notification-service-actividad\evidencias\notification-service-reconocimiento\java\codigo\notification-service"
```

## 2. Preparar la conexión sin escribir credenciales en el documento

El siguiente bloque lee las variables del contenedor PostgreSQL existente y construye el DSN solamente en memoria:

```powershell
$pg = docker inspect notification-recognition-postgres-1 | ConvertFrom-Json
$pgEnv = @{}

foreach ($entry in $pg[0].Config.Env) {
    $parts = $entry -split '=', 2
    if ($parts.Count -eq 2) {
        $pgEnv[$parts[0]] = $parts[1]
    }
}

$dbUser = [uri]::EscapeDataString($pgEnv['POSTGRES_USER'])
$dbPassword = [uri]::EscapeDataString($pgEnv['POSTGRES_PASSWORD'])
$dbName = [uri]::EscapeDataString($pgEnv['POSTGRES_DB'])
$env:NOTIFICATION_DB_DSN = "postgres://${dbUser}:${dbPassword}@localhost:15432/${dbName}?sslmode=disable"
```

Importante: las llaves de `${dbUser}` evitan que PowerShell interprete el carácter `:` como parte del nombre de la variable.

## 3. Ejecutar la API Java en un puerto libre

```powershell
$env:SPRING_PROFILES_ACTIVE = 'api'
$env:PORT = '28081'
$env:OTEL_EXPORTER_OTLP_ENDPOINT = 'http://localhost:4318'
$env:OTEL_EXPORTER_OTLP_PROTOCOL = 'http/protobuf'
$env:OTEL_SERVICE_NAME = 'notification-api-java-recognition'

& 'C:\Program Files\Java\jdk-24\bin\java.exe' `
  -jar '.\target\notification-service-0.0.1-SNAPSHOT.jar'
```

Esta terminal queda ocupada mientras la API está activa. Las siguientes órdenes se ejecutan en otra ventana de PowerShell.

## 4. Verificar salud y disponibilidad

```powershell
Invoke-RestMethod -Uri 'http://localhost:28081/health' -Method Get

Invoke-RestMethod -Uri 'http://localhost:28081/ready' -Method Get |
    ConvertTo-Json -Depth 5
```

Resultados esperados: `status: ok` y comprobación `database` con `ok: true`.

## 5. Enviar una solicitud válida

```powershell
$payload = @{
    recipient_id    = '11111111-1111-4111-8111-111111111101'
    recipient_email = 'hu001-java@example.com'
    channel         = 'EMAIL'
    subject         = 'Evidencia Java HU-001'
    source_service  = 'recognition-demo'
} | ConvertTo-Json

$created = Invoke-WebRequest `
    -Uri 'http://localhost:28081/notifications' `
    -Method Post `
    -ContentType 'application/json' `
    -Body $payload `
    -UseBasicParsing

"HTTP $($created.StatusCode)"
$notification = $created.Content | ConvertFrom-Json
$notification | ConvertTo-Json -Depth 5
```

El resultado esperado es HTTP `202`, un UUID y `send_status: PENDING`.

## 6. Consultar la notificación creada

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:28081/notifications/$($notification.id)" `
    -Method Get |
    ConvertTo-Json -Depth 5
```

## 7. Probar la validación con un canal inválido

```powershell
$invalidPayload = @{
    recipient_id    = '11111111-1111-4111-8111-111111111102'
    recipient_email = 'hu001-java-invalid@example.com'
    channel         = 'SMS'
    subject         = 'Solicitud invalida Java HU-001'
    source_service  = 'recognition-demo'
} | ConvertTo-Json

try {
    Invoke-RestMethod `
        -Uri 'http://localhost:28081/notifications' `
        -Method Post `
        -ContentType 'application/json' `
        -Body $invalidPayload
} catch {
    "HTTP $([int]$_.Exception.Response.StatusCode)"
    $_.ErrorDetails.Message
}
```

Resultado esperado: HTTP `400`, código `VALIDATION_ERROR` y mensaje indicando que el canal debe ser `EMAIL` o `IN_APP`.

## 8. Verificar la persistencia con un SELECT

Reemplazar el UUID por el obtenido en el POST:

```powershell
docker exec notification-recognition-postgres-1 psql `
  -U design_software_app `
  -d design-software-develop `
  -c "SELECT id, recipient_email, channel, send_status, subject, source_service FROM notification.sent_notification WHERE id = '911bbfce-ff30-441a-9c9a-2ca8c4734724'::uuid;"
```

La consulta es de solo lectura y no modifica el contenedor ni sus datos.
