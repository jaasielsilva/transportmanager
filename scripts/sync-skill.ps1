<#
.SYNOPSIS
    Sincroniza as copias da SKILL.md a partir da fonte unica.

.DESCRIPTION
    A skill vive em tres lugares (docs/, .cursor/, .claude/). Manter as tres na
    mao garante divergencia. A fonte da verdade e docs/skills/<nome>/SKILL.md;
    este script propaga.

    Use -Check no CI: falha se as copias divergirem, em vez de sobrescrever.

.EXAMPLE
    .\scripts\sync-skill.ps1
    .\scripts\sync-skill.ps1 -Check
#>
param(
    [switch]$Check,
    [string]$Raiz = (Resolve-Path "$PSScriptRoot\..")
)

$ErrorActionPreference = "Stop"

$fonte = Get-ChildItem -Path "$Raiz\docs\skills" -Filter SKILL.md -Recurse -ErrorAction SilentlyContinue |
         Select-Object -First 1
if (-not $fonte) {
    Write-Error "Nao encontrei docs/skills/*/SKILL.md"
}

$nome   = Split-Path (Split-Path $fonte.FullName -Parent) -Leaf
$copias = @("$Raiz\.cursor\skills\$nome\SKILL.md", "$Raiz\.claude\skills\$nome\SKILL.md")

$divergentes = @()
foreach ($copia in $copias) {
    $iguais = (Test-Path $copia) -and
              ((Get-FileHash $fonte.FullName).Hash -eq (Get-FileHash $copia).Hash)

    if ($iguais) { continue }

    if ($Check) {
        $divergentes += $copia
    } else {
        New-Item -ItemType Directory -Force -Path (Split-Path $copia -Parent) | Out-Null
        Copy-Item $fonte.FullName $copia -Force
        Write-Host "sincronizado: $copia" -ForegroundColor Green
    }
}

if ($Check -and $divergentes) {
    Write-Host "Copias da skill divergentes da fonte:" -ForegroundColor Red
    $divergentes | ForEach-Object { Write-Host "  $_" }
    Write-Host "Rode: .\scripts\sync-skill.ps1"
    exit 1
}

if (-not $Check) { Write-Host "Skill '$nome' sincronizada." -ForegroundColor Cyan }
