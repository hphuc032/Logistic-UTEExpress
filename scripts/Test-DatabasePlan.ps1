$ErrorActionPreference = 'Stop'
$manifestPath = Join-Path $PSScriptRoot '../docs/database-dependencies.json'
$tables = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$names = @($tables | ForEach-Object { $_.table })
if ($tables.Count -ne 30 -or @($names | Select-Object -Unique).Count -ne 30) {
    throw 'The Master Plan requires exactly 30 distinct business tables.'
}
foreach ($table in $tables) {
    if ($table.owner -notin @('HP', 'TD', 'QD')) { throw "Unknown owner: $($table.table)" }
    foreach ($dependency in $table.dependsOn) {
        if ($dependency -notin $names) { throw "Unknown table dependency: $dependency" }
    }
}
$ordered = [System.Collections.Generic.List[string]]::new()
while ($ordered.Count -lt $tables.Count) {
    $ready = @($tables | Where-Object {
        $_.table -notin $ordered -and
        @($_.dependsOn | Where-Object { $_ -notin $ordered }).Count -eq 0
    })
    if ($ready.Count -eq 0) { throw 'Foreign-key dependency cycle detected.' }
    foreach ($table in $ready) { $ordered.Add($table.table) }
}
Write-Output 'PASS: 30 distinct tables, known owners/references, no dependency cycle.'
Write-Output ($ordered -join ' -> ')
