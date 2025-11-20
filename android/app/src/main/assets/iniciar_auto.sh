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

# Verifica se Python está instalado
if ! command -v python3 &> /dev/null; then
    echo "❌ Erro: Python3 não encontrado!"
    echo "Execute: bash INSTALAR_AUTO.sh"
    exit 1
fi

# Verifica se tinytuya está instalado
if ! python3 -c "import tinytuya" 2>/dev/null; then
    echo "⚠️  tinytuya não encontrado. Instalando..."
    python3 -m pip install tinytuya --quiet 2>/dev/null || {
        echo "❌ Erro ao instalar tinytuya"
        echo "Execute: pip install tinytuya"
        exit 1
    }
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

# Inicia em background e salva logs
echo "Iniciando servidor..."
python3 servidor_auto.py >> servidor.log 2>&1 &
NEW_PID=$!

# Aguarda um momento
sleep 1

# Verifica se o processo ainda existe
if ! ps -p "$NEW_PID" > /dev/null 2>&1; then
    echo "❌ Erro: Servidor parou imediatamente!"
    echo ""
    echo "📋 Últimas linhas do log:"
    tail -20 servidor.log 2>/dev/null || echo "Log vazio ou não encontrado"
    echo ""
    echo "🔍 Tente executar manualmente para ver o erro:"
    echo "python3 servidor_auto.py"
    rm -f servidor.pid
    exit 1
fi

echo "$NEW_PID" > servidor.pid

# Aguarda mais um pouco para o servidor inicializar
sleep 2

# Verifica novamente
if ps -p "$NEW_PID" > /dev/null 2>&1; then
    # Testa se o servidor HTTP está respondendo
    if curl -s http://localhost:8080/status > /dev/null 2>&1; then
        echo "✅ Servidor iniciado e respondendo (PID: $NEW_PID)"
        echo "📋 Logs: tail -f servidor.log"
        echo "🛑 Parar: kill $NEW_PID"
        echo "🌐 Status: curl http://localhost:8080/status"
    else
        echo "⚠️  Processo rodando mas servidor não responde ainda"
        echo "📋 Verifique logs: tail -20 servidor.log"
        echo "🛑 Parar: kill $NEW_PID"
    fi
else
    echo "❌ Erro: Servidor parou após iniciar!"
    echo "📋 Últimas linhas do log:"
    tail -30 servidor.log
    rm -f servidor.pid
    exit 1
fi

