#!/bin/bash
# Script para iniciar o servidor em modo automático
# Roda em background e se reinicia automaticamente

# Vai para ~/servidorzinho (onde os arquivos devem estar)
cd ~/servidorzinho 2>/dev/null || {
    echo "❌ Erro: Pasta ~/servidorzinho não encontrada!"
    echo "Execute primeiro: bash ~/storage/downloads/MRIT_Server/copy_to_termux.sh"
    exit 1
}

# Verifica se servidor_auto.py existe
if [ ! -f "servidor_auto.py" ]; then
    echo "❌ Erro: servidor_auto.py não encontrado em ~/servidorzinho"
    echo "Execute: bash ~/storage/downloads/MRIT_Server/copy_to_termux.sh"
    exit 1
fi

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

# Aguarda um pouco para verificar se iniciou
sleep 1
if ps -p "$NEW_PID" > /dev/null 2>&1; then
    echo "✅ Servidor iniciado em background (PID: $NEW_PID)"
    echo "📋 Logs: tail -f ~/servidorzinho/servidor.log"
    echo "🛑 Parar: kill $NEW_PID"
    echo "🌐 Testar: curl http://localhost:8080/status"
else
    echo "❌ Erro: Servidor não iniciou. Verifique os logs:"
    echo "tail -20 ~/servidorzinho/servidor.log"
    rm -f servidor.pid
    exit 1
fi

