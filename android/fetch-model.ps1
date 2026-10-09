$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$items = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'dependencies.json') -Raw | ConvertFrom-Json
foreach ($item in $items) {
  $target = [IO.Path]::GetFullPath((Join-Path $projectRoot $item.path))
  if (-not $target.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar)) { throw 'Invalid dependency path' }
  New-Item -ItemType Directory -Force (Split-Path -Parent $target) | Out-Null
  if (-not (Test-Path -LiteralPath $target)) { Invoke-WebRequest -Uri $item.url -OutFile $target }
  $actual = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
  if ($actual -ne $item.sha) { throw "SHA256 mismatch: $($item.path)" }
  Write-Output ("Verified " + $item.path)
}
