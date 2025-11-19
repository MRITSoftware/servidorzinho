# Script PowerShell para preparar arquivos para o APK

Write-Host "📦 Preparando arquivos para o APK..." -ForegroundColor Cyan

# Cria diretório assets se não existir
$assetsDir = "android\app\src\main\assets"
if (-not (Test-Path $assetsDir)) {
    New-Item -ItemType Directory -Path $assetsDir -Force | Out-Null
    Write-Host "✅ Pasta criada: $assetsDir" -ForegroundColor Green
}

# Lista de arquivos para copiar
$arquivos = @(
    "servidor_auto.py",
    "iniciar_auto.sh",
    "parar.sh",
    "INSTALAR_AUTO.sh",
    "setup_boot.sh",
    "requirements.txt"
)

# Copia cada arquivo
Write-Host "📋 Copiando arquivos..." -ForegroundColor Yellow
foreach ($arquivo in $arquivos) {
    if (Test-Path $arquivo) {
        Copy-Item $arquivo -Destination $assetsDir -Force
        Write-Host "  ✅ $arquivo" -ForegroundColor Green
    } else {
        Write-Host "  ⚠️  $arquivo não encontrado!" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "✅ Arquivos copiados para $assetsDir" -ForegroundColor Green
Write-Host ""
Write-Host "📌 Próximos passos:" -ForegroundColor Cyan
Write-Host "   1. Abra o Android Studio"
Write-Host "   2. File → Open → Selecione a pasta 'android'"
Write-Host "   3. Build → Build Bundle(s) / APK(s) → Build APK(s)"
Write-Host ""
Write-Host "📦 O APK estará em: android\app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Yellow

