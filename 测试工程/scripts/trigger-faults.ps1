# 手动触发各缺陷接口，观察 logs/demo-ops-app.log
$ErrorActionPreference = "Continue"
$base = "http://127.0.0.1:18080/api"

Write-Host "== health =="
Invoke-RestMethod "$base/healthz" | ConvertTo-Json -Compress

$cases = @(
    @{ name = "NPE";               method = "GET";  url = "$base/users/999/email-mask" },
    @{ name = "NumberFormat";      method = "POST"; url = "$base/orders?userId=1&quantity=abc" },
    @{ name = "DateParse";         method = "GET";  url = "$base/reports/biz-date?date=2026-07-31" },
    @{ name = "IndexOutOfBounds";  method = "GET";  url = "$base/reports/orders?page=99&size=20" },
    @{ name = "NoSuchFile";        method = "GET";  url = "$base/inventory/template" },
    @{ name = "StringIndex";       method = "GET";  url = "$base/inventory/sku/A" },
    @{ name = "ConcurrentMod";     method = "POST"; url = "$base/orders/cleanup" }
)

foreach ($c in $cases) {
    Write-Host ""
    Write-Host "== $($c.name) =="
    try {
        if ($c.method -eq "POST") {
            Invoke-WebRequest -Uri $c.url -Method POST -UseBasicParsing | Select-Object StatusCode, Content
        }
        else {
            Invoke-WebRequest -Uri $c.url -Method GET -UseBasicParsing | Select-Object StatusCode, Content
        }
    }
    catch {
        Write-Host $_.Exception.Message
        if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message }
    }
}

Write-Host ""
Write-Host "完成。请查看 logs/demo-ops-app.log"
