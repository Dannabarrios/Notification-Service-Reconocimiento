# Comandos y consultas reproducibles — HU-007

## 1. Health y readiness

```powershell
"=== API :8080 ==="
Invoke-RestMethod -Uri "http://localhost:8080/health" -Method Get
Invoke-RestMethod -Uri "http://localhost:8080/ready" -Method Get | ConvertTo-Json -Depth 5

"=== WORKER :8081 ==="
Invoke-RestMethod -Uri "http://localhost:8081/health" -Method Get
Invoke-RestMethod -Uri "http://localhost:8081/ready" -Method Get | ConvertTo-Json -Depth 5
```

## 2. Abrir Grafana

```text
http://localhost:3000
```

Grafana tiene acceso anónimo local y fuentes provisionadas para Prometheus, Tempo y Loki.

## 3. Consultar métricas

En Explore, seleccionar Prometheus y ejecutar:

```promql
http_server_requests_total
```

Usar `Last 1 hour`. Si se necesita comprobar primero la fuente:

```promql
up
```

El botón azul con flecha circular ejecuta la consulta.

## 4. Consultar trazas

En Explore, seleccionar Tempo, modo TraceQL/Code:

```traceql
{ resource.service.name = "notification-api" }
```

Abrir un Trace ID para ver servicio, operación, duración y spans.

## 5. Consultar logs

En Explore, seleccionar Loki, modo Code:

```logql
{service_name="notification-api"}
```

Con `Last 1 hour`, el resultado observado fue `No logs found` debido a que stdout de PowerShell no se recolecta.

## Qué explicar

1. Liveness no verifica dependencias; readiness sí.
2. Prometheus consulta métricas agregadas.
3. Tempo consulta recorridos individuales.
4. Loki necesita recibir logs; provisionarlo no garantiza ingestión.
5. Las etiquetas métricas no deben contener UUID.

## Seguridad

- No mostrar variables de conexión ni credenciales.
- No editar fuentes de datos o dashboards compartidos.
- No detener dependencias para simular fallos.
- Evitar datos personales en etiquetas y logs.
