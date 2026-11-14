# collect-evidence.ps1
# Runs the full MTG demo flow against a running app and saves every request/response as JSON
# (sample inputs/outputs + execution evidence for the NestforAwhile submission).
#
# Usage (from the repo root):
#   powershell -ExecutionPolicy Bypass -File docs\evidence\collect-evidence.ps1 -BaseUrl https://YOUR-APP.onrender.com -ManagerPassword YOUR_MANAGER_PASSWORD
#   powershell -ExecutionPolicy Bypass -File docs\evidence\collect-evidence.ps1                # defaults: localhost, manager/manager
#
# Note: this performs a real checkout for CLI-1002 (stock is decremented and it shows up in the grant report).

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$ManagerPassword = "manager",
    [string]$ClientCode = "CLI-1002",
    [string]$OutDir = "$PSScriptRoot\api-samples"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$auth = @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("manager:$ManagerPassword")) }
$log = New-Object System.Collections.Generic.List[string]

function Save($name, $request, $response, $status = 200) {
    $doc = [ordered]@{ step = $name; request = $request; status = $status; response = $response; capturedAt = (Get-Date).ToString("s") }
    $doc | ConvertTo-Json -Depth 12 | Out-File -Encoding utf8 (Join-Path $OutDir "$name.json")
    $log.Add(("{0,-34} HTTP {1}" -f $name, $status))
    Write-Host ("saved {0}" -f $name) -ForegroundColor Green
}

function Call($method, $path, $body = $null, [switch]$Staff) {
    $p = @{ Method = $method; Uri = "$BaseUrl$path"; ContentType = "application/json" }
    if ($Staff) { $p.Headers = $auth }
    if ($body -ne $null) { $p.Body = ($body | ConvertTo-Json -Depth 6 -Compress) }
    return Invoke-RestMethod @p
}

function CallExpectError($method, $path, $body = $null, [switch]$Staff) {
    try { Call $method $path $body -Staff:$Staff | Out-Null; return @{ status = 200; body = "UNEXPECTED SUCCESS" } }
    catch {
        $code = [int]$_.Exception.Response.StatusCode
        $msg = $_.ErrorDetails.Message
        $parsed = $null; try { $parsed = $msg | ConvertFrom-Json } catch { $parsed = $msg }
        return @{ status = $code; body = $parsed }
    }
}

Write-Host "Collecting evidence from $BaseUrl" -ForegroundColor Cyan

# 0. Wake the app (Render free tier sleeps) and make room on the floor
try { Invoke-WebRequest "$BaseUrl/" -UseBasicParsing -TimeoutSec 120 | Out-Null } catch {}
Save "00-capacity-before" @{ GET = "/api/capacity" } (Call GET "/api/capacity")
Save "00b-queue-tick" @{ POST = "/api/admin/queue/tick" } (Call POST "/api/admin/queue/tick" -Staff)

# 1. Errors are clear
$e = CallExpectError POST "/api/checkin" @{ clientCode = "CLI-9999" }
Save "01a-checkin-unknown-client" @{ POST = "/api/checkin"; body = @{ clientCode = "CLI-9999" } } $e.body $e.status

# 2. Check-in (US-1.1, 1.2, 1.3)
$s = Call POST "/api/checkin" @{ clientCode = $ClientCode }
Save "01b-checkin" @{ POST = "/api/checkin"; body = @{ clientCode = $ClientCode } } $s
$token = $s.sessionToken
if ($s.status -ne "ACTIVE") {
    Write-Host "Session is $($s.status) (floor full?). Free a spot or run in Neon: UPDATE shopping_session SET status='EXPIRED' WHERE status IN ('ACTIVE','WAITING');" -ForegroundColor Yellow
    exit 1
}

# 3. Recommendations (US-3.2)
Save "02-recommendations" @{ GET = "/api/session/{token}/recommendations" } (Call GET "/api/session/$token/recommendations")

# 4. Aisle scans (US-3.1): Red (allergy), Red (potassium), Yellow (capped), Green
$scans = [ordered]@{
    "03a-scan-RED-almonds-tree-nut-allergy" = "071234567855"
    "03b-scan-RED-spinach-potassium"        = "071234567801"
    "03c-scan-YELLOW-greek-yogurt"          = "071234567879"
    "03d-scan-GREEN-roma-tomatoes"          = "071234567802"
    "03e-scan-GREEN-cucumber"               = "071234567812"
}
$red = @()
foreach ($k in $scans.Keys) {
    $r = Call POST "/api/session/$token/scan" @{ code = $scans[$k] }
    Save $k @{ POST = "/api/session/{token}/scan"; body = @{ code = $scans[$k] } } $r
    if ($r.item.flag -eq "RED") { $red += $r.item.productId }
}
$e = CallExpectError POST "/api/session/$token/scan" @{ code = "000000000000" }
Save "03f-scan-unknown-barcode" @{ POST = "/api/session/{token}/scan"; body = @{ code = "000000000000" } } $e.body $e.status

# 5. Cart + recipes (US-3.3)
Save "04a-cart" @{ GET = "/api/session/{token}/cart" } (Call GET "/api/session/$token/cart")
Save "04b-recipes" @{ GET = "/api/session/{token}/recipes" } (Call GET "/api/session/$token/recipes?refresh=true")

# 6. Checkout hard-stop (US-4.1)
Save "05a-checkout-validate-HARD_STOP" @{ POST = "/api/checkout/{token}/validate"; body = @{} } (Call POST "/api/checkout/$token/validate" @{} -Staff)
$e = CallExpectError POST "/api/checkout/$token/complete" @{} -Staff
Save "05b-complete-BLOCKED-409" @{ POST = "/api/checkout/{token}/complete"; body = @{} } $e.body $e.status

$last = $null
foreach ($productId in $red) {
    $last = Call POST "/api/checkout/$token/remove" @{ productId = $productId; reason = "Allergy / over nutrient limit" } -Staff
    Save ("06-remove-restricted-product-{0}" -f $productId) @{ POST = "/api/checkout/{token}/remove"; body = @{ productId = $productId } } $last
}
Save "06z-checkout-validate-OK" @{ POST = "/api/checkout/{token}/validate"; body = @{} } (Call POST "/api/checkout/$token/validate" @{} -Staff)

# 7. Complete: atomic FIFO decrement + receipt (US-4.1, 4.3)
Save "07-checkout-complete-receipt" @{ POST = "/api/checkout/{token}/complete"; body = @{} } (Call POST "/api/checkout/$token/complete" @{} -Staff)
Save "07b-session-after-checkout" @{ GET = "/api/session/{token}" } (Call GET "/api/session/$token")

# 8. Restock route + expiring lots (US-4.2, 4.3)
Save "08-restock-route" @{ GET = "/api/restock" } (Call GET "/api/restock" -Staff)

# 9. Analytics (US-5.1, 5.2)
Save "09a-analytics-outcomes" @{ GET = "/api/analytics/outcomes" } (Call GET "/api/analytics/outcomes" -Staff)
Save "09b-analytics-grants" @{ GET = "/api/analytics/grants" } (Call GET "/api/analytics/grants" -Staff)
(Invoke-WebRequest "$BaseUrl/api/analytics/grants.csv" -Headers $auth -UseBasicParsing).Content | Out-File -Encoding utf8 (Join-Path $OutDir "09c-grant-attribution.csv")
(Invoke-WebRequest "$BaseUrl/api/analytics/outcomes.csv" -Headers $auth -UseBasicParsing).Content | Out-File -Encoding utf8 (Join-Path $OutDir "09d-program-outcomes.csv")

# 10. AI safety harness (model evaluation)
$h = Call GET "/api/admin/ai-harness?runs=2" -Staff
$h | ConvertTo-Json -Depth 12 | Out-File -Encoding utf8 (Join-Path $PSScriptRoot "ai-harness-results.json")
Save "10-ai-harness" @{ GET = "/api/admin/ai-harness?runs=2" } $h

$summary = @(
  "MTG evidence run: $(Get-Date -Format s)  base=$BaseUrl  client=$ClientCode",
  "",
  $log,
  "",
  ("AI harness: runs={0} aiServed={1} fallbacks={2} restrictedCaughtByValidator={3} servedRestrictedViolations={4} servedOutOfStock={5} avgLatencyMs={6} passed={7}" -f `
     $h.runs, $h.aiServed, $h.fallbacks, $h.restrictedCaughtByValidator, $h.servedRestrictedViolations, $h.servedOutOfStock, $h.avgLatencyMs, $h.passed)
)
$summary | Out-File -Encoding utf8 (Join-Path $OutDir "_summary.txt")
Write-Host ""
$summary | ForEach-Object { Write-Host $_ }
Write-Host "`nDone. Files are in $OutDir" -ForegroundColor Cyan
