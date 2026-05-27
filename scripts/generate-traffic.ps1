param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$Workers = 12,
    [int]$DurationSeconds = 120
)

$end = (Get-Date).AddSeconds($DurationSeconds)
$jobs = @()

1..$Workers | ForEach-Object {
    $jobs += Start-Job -ScriptBlock {
        param($BaseUrl, $end)
        $rnd = New-Object System.Random

        while ((Get-Date) -lt $end) {
            $roll = $rnd.Next(100)
            if ($roll -lt 30) {
                $url = "$BaseUrl/"
            } elseif ($roll -lt 65) {
                $names = @("alex", "sam", "maria", "lee", "nora", "vik")
                $name = $names[$rnd.Next($names.Length)]
                $url = "$BaseUrl/greet/$name"
            } elseif ($roll -lt 82) {
                $url = "$BaseUrl/slow"
            } elseif ($roll -lt 95) {
                $url = "$BaseUrl/unstable?failPercent=40"
            } else {
                $url = "$BaseUrl/chatter/20"
            }

            try {
                Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3 -ErrorAction SilentlyContinue | Out-Null
            } catch {
                # Intentionally ignore per-request failures during demo load.
            }

            Start-Sleep -Milliseconds ($rnd.Next(20, 180))
        }
    } -ArgumentList $BaseUrl, $end
}

Write-Host "Started $Workers workers for $DurationSeconds seconds against $BaseUrl"
Wait-Job $jobs | Out-Null
Receive-Job $jobs | Out-Null
Remove-Job $jobs | Out-Null
Write-Host "Traffic run complete"
