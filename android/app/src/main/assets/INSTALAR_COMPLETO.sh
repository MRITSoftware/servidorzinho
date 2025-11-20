#!/bin/bash
# Script para garantir que TUDO está instalado corretamente
# Use este script se a instalação anterior falhou

cd ~/servidorzinho 2>/dev/null || {
    echo "❌ Erro: Execute primeiro: bash ~/storage/downloads/MRIT_Server/copy_to_termux.sh"
    exit 1
}

echo "╔══════════════════════════════════════╗"
echo "║   Reinstalação Completa             ║"
echo "╚══════════════════════════════════════╝"
echo ""
echo "⚠️  Removendo marca de instalação anterior..."
rm -f .installed

echo "📦 Executando instalação completa..."
bash INSTALAR_AUTO.sh

echo ""
echo "✅ Reinstalação concluída!"
echo "🚀 Para iniciar: bash iniciar_auto.sh"

