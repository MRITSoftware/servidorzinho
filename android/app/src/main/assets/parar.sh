#!/bin/bash
# Para o servidor que está rodando em background

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$SCRIPT_DIR/servidor.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "❌ Servidor não está rodando"
    exit 1
fi

PID=$(cat "$PID_FILE")

if ps -p "$PID" > /dev/null 2>&1; then
    kill "$PID"
    rm -f "$PID_FILE"
    echo "✅ Servidor parado"
else
    rm -f "$PID_FILE"
    echo "⚠️  Processo não encontrado (já estava parado)"
fi

