# Comandos de laboratorio — HU-008 Java

## 1. Verificar infraestructura existente

```powershell
docker ps `
  --filter 'name=notification-recognition' `
  --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
```

Este comando solo consulta. No usar `docker compose down`, `docker stop`, `docker rm` ni `docker system prune`.

## 2. Iniciar API y worker

La construcción segura del DSN y las variables de la API están en [HU-001](../hu-001/comandos.md). Las variables AMQP y del worker están en [HU-002](../hu-002/comandos.md).

Puertos usados:

```powershell
$env:PORT = '28081'
$env:WORKER_HEALTH_PORT = '28082'
```

API:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'api'
& 'C:\Program Files\Java\jdk-24\bin\java.exe' `
  -jar '.\target\notification-service-0.0.1-SNAPSHOT.jar'
```

Worker, en otra terminal y con sus variables DB, AMQP y SMTP preparadas:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'worker'
& 'C:\Program Files\Java\jdk-24\bin\java.exe' `
  -jar '.\target\notification-service-0.0.1-SNAPSHOT.jar'
```

## 3. Verificar disponibilidad

```powershell
Invoke-RestMethod 'http://localhost:28081/ready' | ConvertTo-Json -Depth 5
Invoke-RestMethod 'http://localhost:28082/ready' | ConvertTo-Json -Depth 5
```

## 4. Construir el evento

```powershell
$event = @{
    event_id       = '88888888-8888-4888-8888-888888888408'
    event_type     = 'scheduling.schedule.published'
    source_service = 'scheduling-service-java-demo'
    timestamp      = (Get-Date).ToUniversalTime().ToString('o')
    version        = '1.0'
    payload        = @{
        published_by  = '99999999-9999-4999-8999-999999999409'
        schedule_name = 'Java-HU008-20260825'
        ficha         = '3145555'
    }
} | ConvertTo-Json -Depth 10 -Compress

$request = @{
    properties       = @{ content_type = 'application/json' }
    routing_key      = 'scheduling.schedule.published'
    payload          = $event
    payload_encoding = 'string'
} | ConvertTo-Json -Depth 10
```

## 5. Publicar

Con `$headers` preparado como se explica en HU-002:

```powershell
Invoke-RestMethod `
  -Uri 'http://localhost:15672/api/exchanges/%2F/scheduling-events/publish' `
  -Method Post -Headers $headers -ContentType 'application/json' -Body $request
```

## 6. Verificar MailHog

```powershell
$mail = Invoke-RestMethod 'http://localhost:18025/api/v2/messages?limit=1'
$mail.items[0].Content.Headers.Subject[0]
$mail.items[0].Content.Headers.To[0]
```

## 7. Verificar PostgreSQL y Outbox

```powershell
docker exec notification-recognition-postgres-1 psql `
  -U design_software_app -d design-software-develop `
  -c "SELECT n.id,n.send_status,n.subject,n.body_summary,t.code FROM notification.sent_notification n LEFT JOIN notification.notification_template t ON t.id=n.template_id WHERE n.source_event_id='88888888-8888-4888-8888-888888888408'::uuid;"

docker exec notification-recognition-postgres-1 psql `
  -U design_software_app -d design-software-develop `
  -c "SELECT o.event_type,(o.published_at IS NOT NULL) AS publicado FROM notification.outbox o JOIN notification.sent_notification n ON o.payload->>'notification_id'=n.id::text WHERE n.source_event_id='88888888-8888-4888-8888-888888888408'::uuid;"
```
