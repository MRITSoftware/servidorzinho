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

# Inicia em background e redireciona erros para log
nohup python3 servidor_auto.py >> servidor.log 2>&1 &

NEW_PID=$!
echo "$NEW_PID" > servidor.pid

# Aguarda um pouco para verificar se iniciou
sleep 2
if ps -p "$NEW_PID" > /dev/null 2>&1; then
    echo "✅ Servidor iniciado em background (PID: $NEW_PID)"
    echo "📋 Logs: tail -f servidor.log"
    echo "🛑 Parar: kill $NEW_PID"
    echo "🌐 Testar: curl http://localhost:8080/status"
    echo ""
    echo "Verificando se servidor responde..."
    sleep 1
    if curl -s http://localhost:8080/status > /dev/null 2>&1; then
        echo "✅ Servidor respondendo na porta 8080"
    else
        echo "⚠️ Servidor iniciado mas não responde ainda. Verifique logs."
    fi
else
    echo "❌ Erro: Servidor não iniciou. Verifique os logs:"
    echo "tail -30 servidor.log"
    rm -f servidor.pid
    exit 1
fi

