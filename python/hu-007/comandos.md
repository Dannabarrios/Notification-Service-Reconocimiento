# Comandos — HU-007 Python

Prometheus mediante Grafana:

```powershell
$base='http://localhost:3000/api/datasources/proxy/uid/PBFA97CFB590B2093/api/v1/query?query='
$q='http_server_requests_total{exported_job="notification-api"}'
Invoke-RestMethod ($base+[uri]::EscapeDataString($q))
```

Tempo:

```powershell
$q='{ resource.service.name = "notification-api" }'
Invoke-RestMethod ('http://localhost:3000/api/datasources/proxy/uid/P214B5B846CF3925F/api/search?q='+[uri]::EscapeDataString($q)+'&limit=10')
```

Loki: consultar `/loki/api/v1/labels`; en la prueba devolvió lista vacía.
