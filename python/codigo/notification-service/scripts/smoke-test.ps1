$ErrorActionPreference = "Stop"
$api = if ($env:NOTIFICATION_API_URL) { $env:NOTIFICATION_API_URL } else { "http://localhost:38081" }
$worker = if ($env:NOTIFICATION_WORKER_URL) { $env:NOTIFICATION_WORKER_URL } else { "http://localhost:38082" }

Write-Host "[1/5] API health"
Invoke-RestMethod "$api/health" | ConvertTo-Json -Compress

Write-Host "[2/5] API readiness"
Invoke-RestMethod "$api/ready" | ConvertTo-Json -Depth 5 -Compress

Write-Host "[3/5] Create notification"
$body = @{
  recipient_id = "22222222-2222-2222-2222-222222222222"
  recipient_email = "demo@example.com"
  channel = "EMAIL"
  subject = "FastAPI migration demo"
  source_service = "manual-demo"
} | ConvertTo-Json
$created = Invoke-RestMethod -Method Post -Uri "$api/notifications" -ContentType "application/json" -Body $body
$created | ConvertTo-Json -Compress

Write-Host "[4/5] Read notification"
Invoke-RestMethod "$api/notifications/$($created.id)" | ConvertTo-Json -Compress

Write-Host "[5/5] Worker health/readiness"
Invoke-RestMethod "$worker/health" | ConvertTo-Json -Compress
Invoke-RestMethod "$worker/ready" | ConvertTo-Json -Depth 5 -Compress

Write-Host "Smoke test OK"
