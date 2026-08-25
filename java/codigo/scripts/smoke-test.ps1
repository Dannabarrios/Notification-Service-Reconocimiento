param(
    [string]$ApiBase = "http://localhost:28081",
    [string]$MailHogBase = "http://localhost:28025"
)
$ErrorActionPreference = "Stop"

Write-Host "1/4 GET /health"
Invoke-RestMethod "$ApiBase/health" | ConvertTo-Json -Depth 5

Write-Host "2/4 GET /ready"
Invoke-RestMethod "$ApiBase/ready" | ConvertTo-Json -Depth 5

$recipient = [guid]::NewGuid().ToString()
$body = @{
    recipient_id = $recipient
    recipient_email = "demo@sena.local"
    channel = "EMAIL"
    subject = "Prueba Java"
    template_code = "SCHEDULE_PUBLISHED"
    template_vars = @{ schedule_name = "Horario Demo"; ficha = "2999999" }
    source_service = "demo"
} | ConvertTo-Json -Depth 5

Write-Host "3/4 POST /notifications"
$created = Invoke-RestMethod -Method Post -Uri "$ApiBase/notifications" -ContentType "application/json" -Body $body
$created | ConvertTo-Json -Depth 5

Write-Host "4/4 GET /notifications/$($created.id)"
Invoke-RestMethod "$ApiBase/notifications/$($created.id)" | ConvertTo-Json -Depth 5

Write-Host "MailHog UI: $MailHogBase"
