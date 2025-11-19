#!/bin/bash
# Script para preparar arquivos para compilar o APK

echo "📦 Preparando arquivos para o APK..."

# Cria diretório assets se não existir
mkdir -p android/app/src/main/assets

# Copia arquivos necessários
echo "📋 Copiando arquivos Python..."
cp servidor_auto.py android/app/src/main/assets/
cp iniciar_auto.sh android/app/src/main/assets/
cp parar.sh android/app/src/main/assets/
cp INSTALAR_AUTO.sh android/app/src/main/assets/
cp setup_boot.sh android/app/src/main/assets/
cp requirements.txt android/app/src/main/assets/

# Torna scripts executáveis (será aplicado no Android)
chmod +x android/app/src/main/assets/*.sh

echo "✅ Arquivos copiados para android/app/src/main/assets/"
echo ""
echo "📌 Próximos passos:"
echo "   1. Abra o Android Studio"
echo "   2. File → Open → Selecione a pasta 'android'"
echo "   3. Build → Build Bundle(s) / APK(s) → Build APK(s)"
echo ""
echo "📦 O APK estará em: android/app/build/outputs/apk/debug/app-debug.apk"

