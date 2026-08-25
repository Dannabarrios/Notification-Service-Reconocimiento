param(
    [string]$RabbitManagementBase = "http://localhost:25673",
    [string]$RabbitUser = "app",
    [string]$RabbitPassword = "app"
)
$ErrorActionPreference = "Stop"

$eventId = [guid]::NewGuid().ToString()
$instructor = [guid]::NewGuid().ToString()
$event = @{
    event_id = $eventId
    event_type = "scheduling.schedule.published"
    version = "1.0"
    timestamp = (Get-Date).ToUniversalTime().ToString("o")
    source_service = "scheduling-service"
    payload = @{
        published_by = $instructor
        schedule_name = "Horario Demo"
        ficha = "2999999"
    }
}
$eventJson = $event | ConvertTo-Json -Compress -Depth 6
$publishRequest = @{
    properties = @{ content_type = "application/json" }
    routing_key = "scheduling.schedule.published"
    payload = $eventJson
    payload_encoding = "string"
} | ConvertTo-Json -Compress -Depth 6

$pair = "${RabbitUser}:${RabbitPassword}"
$auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
$headers = @{ Authorization = "Basic $auth" }
$url = "$RabbitManagementBase/api/exchanges/%2F/scheduling-events/publish"
$result = Invoke-RestMethod -Method Post -Uri $url -Headers $headers -ContentType "application/json" -Body $publishRequest

if (-not $result.routed) {
    throw "RabbitMQ accepted the request but did not route the event. Verify notification-worker is running."
}
Write-Host "Evento publicado y enrutado: $eventId"
Write-Host "Revise MailHog en http://localhost:28025 y las tablas notification.sent_notification / notification.outbox."
