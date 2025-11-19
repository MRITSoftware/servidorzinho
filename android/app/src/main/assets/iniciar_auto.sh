#!/bin/bash
# Script para iniciar o servidor em modo automático
# Roda em background e se reinicia automaticamente

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Verifica se já está rodando
if [ -f "servidor.pid" ]; then
    OLD_PID=$(cat servidor.pid)
    if ps -p "$OLD_PID" > /dev/null 2>&1; then
        echo "⚠️  Servidor já está rodando (PID: $OLD_PID)"
        exit 0
    else
        rm -f servidor.pid
    fi
fi

# Inicia em background
nohup python3 servidor_auto.py > /dev/null 2>&1 &

NEW_PID=$!
echo "$NEW_PID" > servidor.pid

echo "✅ Servidor iniciado em background (PID: $NEW_PID)"
echo "📋 Logs: tail -f servidor.log"
echo "🛑 Parar: kill $NEW_PID"

