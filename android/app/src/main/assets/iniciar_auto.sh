#!/bin/bash
# Script ULTRA SIMPLES para iniciar o servidor - foca em fazer funcionar

# Ir para o diretório
cd ~/servidorzinho 2>/dev/null || {
    echo "❌ Erro: Pasta ~/servidorzinho não encontrada!"
    exit 1
}

# Verificar se arquivo existe
if [ ! -f "servidor_auto.py" ]; then
    echo "❌ Erro: servidor_auto.py não encontrado!"
    exit 1
fi

# Verificar Python
if ! command -v python3 &> /dev/null; then
    echo "❌ Erro: Python3 não encontrado!"
    echo "Execute: bash INSTALAR_AUTO.sh"
    exit 1
fi

# Verificar dependências
if ! python3 -c "import tinytuya" 2>/dev/null; then
    echo "⚠️  Dependências não instaladas. Instalando..."
    bash INSTALAR_AUTO.sh || {
        echo "❌ Erro na instalação"
        exit 1
    }
fi

# Parar servidor antigo se existir
if [ -f "servidor.pid" ]; then
    OLD_PID=$(cat servidor.pid)
    if ps -p "$OLD_PID" > /dev/null 2>&1; then
        echo "⚠️  Parando servidor antigo (PID: $OLD_PID)..."
        kill "$OLD_PID" 2>/dev/null || true
        sleep 1
    fi
    rm -f servidor.pid
fi

# Limpar processos antigos
pkill -f "servidor_auto.py" 2>/dev/null || true
sleep 1

# Iniciar servidor
echo "🚀 Iniciando servidor..."
echo "📋 Logs em: servidor.log"
echo ""

# Executar em background
nohup python3 servidor_auto.py > servidor.log 2>&1 &
NEW_PID=$!

# Salvar PID
echo "$NEW_PID" > servidor.pid

# Aguardar inicialização
sleep 3

# Verificar se está rodando
if ps -p "$NEW_PID" > /dev/null 2>&1; then
    echo "✅ Servidor iniciado! (PID: $NEW_PID)"
    echo ""
    echo "📋 Ver logs: tail -f servidor.log"
    echo "🛑 Parar: kill $NEW_PID"
    echo "🌐 Testar: curl http://localhost:8080/status"
    echo ""
    
    # Tentar verificar se está respondendo
    sleep 2
    if curl -s http://localhost:8080/status > /dev/null 2>&1; then
        echo "✅ Servidor respondendo corretamente!"
    else
        echo "⚠️  Servidor iniciado mas ainda não está respondendo"
        echo "   Aguarde alguns segundos e verifique os logs"
    fi
else
    echo "❌ Erro: Servidor não iniciou!"
    echo ""
    echo "📋 Últimas linhas do log:"
    tail -20 servidor.log 2>/dev/null || echo "Log não encontrado"
    echo ""
    echo "🔍 Testando execução direta:"
    python3 servidor_auto.py 2>&1 | head -30
    rm -f servidor.pid
    exit 1
fi
