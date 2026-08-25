$ErrorActionPreference = "Stop"
$rabbit = if ($env:RABBITMQ_MGMT_URL) { $env:RABBITMQ_MGMT_URL } else { "http://localhost:35673" }
$user = if ($env:RABBITMQ_USER) { $env:RABBITMQ_USER } else { "app" }
$pass = if ($env:RABBITMQ_PASSWORD) { $env:RABBITMQ_PASSWORD } else { "app" }

$eventId = [guid]::NewGuid().ToString()
$envelope = @{
  event_id = $eventId
  event_type = "monitoring.alert.triggered"
  version = "1.0"
  timestamp = [DateTime]::UtcNow.ToString("o")
  source_service = "monitoring-service"
  payload = @{
    affected_entity_type = "Learner"
    affected_entity_id = "10101010-1010-1010-1010-101010101010"
    alert_type_code = "LOW_ATTENDANCE"
  }
} | ConvertTo-Json -Depth 6 -Compress

$publishBody = @{
  properties = @{}
  routing_key = "monitoring.alert.triggered"
  payload = $envelope
  payload_encoding = "string"
} | ConvertTo-Json -Depth 6

$pair = "${user}:${pass}"
$token = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
$headers = @{ Authorization = "Basic $token" }
$result = Invoke-RestMethod -Method Post -Uri "$rabbit/api/exchanges/%2F/monitoring-events/publish" -Headers $headers -ContentType "application/json" -Body $publishBody
$result | ConvertTo-Json -Compress
Write-Host "Published event_id=$eventId. Check MailHog at http://localhost:38025 and the worker logs."
