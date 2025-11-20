# Script para disparar o workflow de build do APK no GitHub
# Requer token do GitHub com permissão para workflows

param(
    [string]$Token = "",
    [string]$Owner = "",
    [string]$Repo = ""
)

# Se não foram fornecidos, tenta pegar do git remoto
if ([string]::IsNullOrEmpty($Owner) -or [string]::IsNullOrEmpty($Repo)) {
    $remoteUrl = git remote get-url origin 2>$null
    if ($remoteUrl) {
        if ($remoteUrl -match "github\.com[:/]([^/]+)/([^/]+?)(?:\.git)?$") {
            $Owner = $Matches[1]
            $Repo = $Matches[2] -replace '\.git$', ''
            Write-Host "✅ Detectado: $Owner/$Repo" -ForegroundColor Green
        }
    }
}

if ([string]::IsNullOrEmpty($Owner) -or [string]::IsNullOrEmpty($Repo)) {
    Write-Host "❌ Erro: Não foi possível detectar Owner/Repo" -ForegroundColor Red
    Write-Host "Uso: .\disparar_workflow.ps1 -Token SEU_TOKEN -Owner SEU_USUARIO -Repo SEU_REPO" -ForegroundColor Yellow
    exit 1
}

if ([string]::IsNullOrEmpty($Token)) {
    Write-Host "⚠️  Token do GitHub não fornecido" -ForegroundColor Yellow
    Write-Host "Você pode:" -ForegroundColor Yellow
    Write-Host "1. Criar um token em: https://github.com/settings/tokens" -ForegroundColor Cyan
    Write-Host "2. Dar permissão 'workflow' ao token" -ForegroundColor Cyan
    Write-Host "3. Executar: .\disparar_workflow.ps1 -Token SEU_TOKEN" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Ou faça manualmente:" -ForegroundColor Yellow
    Write-Host "1. Vá para: https://github.com/$Owner/$Repo/actions" -ForegroundColor Cyan
    Write-Host "2. Clique em 'Build APK'" -ForegroundColor Cyan
    Write-Host "3. Clique em 'Run workflow'" -ForegroundColor Cyan
    exit 0
}

Write-Host "🚀 Disparando workflow 'Build APK'..." -ForegroundColor Green

$headers = @{
    "Accept" = "application/vnd.github.v3+json"
    "Authorization" = "token $Token"
}

$body = @{
    ref = "main"
} | ConvertTo-Json

$url = "https://api.github.com/repos/$Owner/$Repo/actions/workflows/build-apk.yml/dispatches"

try {
    $response = Invoke-RestMethod -Uri $url -Method Post -Headers $headers -Body $body -ContentType "application/json"
    Write-Host "✅ Workflow disparado com sucesso!" -ForegroundColor Green
    Write-Host "📱 Acompanhe em: https://github.com/$Owner/$Repo/actions" -ForegroundColor Cyan
} catch {
    Write-Host "❌ Erro ao disparar workflow:" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    if ($_.ErrorDetails.Message) {
        $errorJson = $_.ErrorDetails.Message | ConvertFrom-Json
        Write-Host "Detalhes: $($errorJson.message)" -ForegroundColor Red
    }
    exit 1
}

