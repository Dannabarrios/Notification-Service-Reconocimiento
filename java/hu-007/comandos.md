# Comandos reproducibles — HU-007 Java

## Salud

```powershell
Invoke-RestMethod 'http://localhost:28081/health'
Invoke-RestMethod 'http://localhost:28081/ready' | ConvertTo-Json -Depth 5
Invoke-RestMethod 'http://localhost:28082/health'
Invoke-RestMethod 'http://localhost:28082/ready' | ConvertTo-Json -Depth 5
```

## Prometheus mediante el datasource de Grafana

```powershell
$prometheus = 'http://localhost:3000/api/datasources/proxy/uid/PBFA97CFB590B2093/api/v1/query?query='
$query = 'http_server_requests_milliseconds_count{exported_job="notification-api"}'
Invoke-RestMethod ($prometheus + [uri]::EscapeDataString($query))
```

Consultas adicionales:

```promql
sum by (channel,status) (notification_delivered_total{exported_job="notification-worker"})
sum(rabbitmq_consumed_total{exported_job="notification-worker"})
```

## Tempo — buscar trazas de la API

```powershell
$traceql = '{ resource.service.name = "notification-api" }'
$url = 'http://localhost:3000/api/datasources/proxy/uid/P214B5B846CF3925F/api/search?q=' +
       [uri]::EscapeDataString($traceql) + '&limit=10'
Invoke-RestMethod $url
```

## Tempo — consultar una traza exacta

```powershell
$traceId = '8720cad3fbe38082d797ad5333587ba2'
Invoke-RestMethod `
  "http://localhost:3000/api/datasources/proxy/uid/P214B5B846CF3925F/api/traces/$traceId"
```

## Loki — comprobar etiquetas

```powershell
$loki = 'http://localhost:3000/api/datasources/proxy/uid/P8E80F9AEF21F6940/loki/api/v1'
Invoke-RestMethod "$loki/labels"
Invoke-RestMethod "$loki/label/service_name/values"
Invoke-RestMethod "$loki/label/job/values"
```

En esta ejecución las tres consultas devolvieron listas vacías.
