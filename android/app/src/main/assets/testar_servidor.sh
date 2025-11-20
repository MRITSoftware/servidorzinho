#!/bin/bash
# Script de diagnóstico - verifica tudo antes de iniciar

echo "=== DIAGNÓSTICO DO SERVIDOR ==="
echo ""

# 1. Verifica diretório
echo "1. Verificando diretório..."
cd ~/servidorzinho 2>/dev/null || {
    echo "❌ Pasta ~/servidorzinho não existe!"
    echo "Execute: bash ~/storage/downloads/MRIT_Server/copy_to_termux.sh"
    exit 1
}
echo "✅ Diretório OK: $(pwd)"
echo ""

# 2. Lista arquivos
echo "2. Arquivos no diretório:"
ls -la
echo ""

# 3. Verifica Python
echo "3. Verificando Python..."
if ! command -v python3 &> /dev/null; then
    echo "❌ Python3 não encontrado!"
    exit 1
fi
echo "✅ Python: $(python3 --version)"
echo ""

# 4. Verifica tinytuya
echo "4. Verificando tinytuya..."
if ! python3 -c "import tinytuya" 2>/dev/null; then
    echo "⚠️  tinytuya não instalado."
    echo "📦 Executando instalação completa (isso pode demorar)..."
    if [ -f "INSTALAR_AUTO.sh" ]; then
        bash INSTALAR_AUTO.sh || {
            echo "❌ Erro na instalação automática"
            echo "💡 Tente executar manualmente: bash INSTALAR_AUTO.sh"
            exit 1
        }
    else
        echo "❌ Script INSTALAR_AUTO.sh não encontrado!"
        echo "💡 Execute primeiro: bash ~/storage/downloads/MRIT_Server/copy_to_termux.sh"
        exit 1
    fi
fi
echo "✅ tinytuya instalado"
echo ""

# 5. Verifica servidor_auto.py
echo "5. Verificando servidor_auto.py..."
if [ ! -f "servidor_auto.py" ]; then
    echo "❌ servidor_auto.py não encontrado!"
    exit 1
fi
echo "✅ servidor_auto.py existe"
echo ""

# 6. Testa sintaxe Python
echo "6. Verificando sintaxe Python..."
python3 -m py_compile servidor_auto.py 2>&1 || {
    echo "❌ Erro de sintaxe no servidor_auto.py!"
    exit 1
}
echo "✅ Sintaxe OK"
echo ""

# 7. Testa importações
echo "7. Testando importações..."
python3 -c "
import sys
sys.path.insert(0, '.')
try:
    import servidor_auto
    print('✅ Importações OK')
except Exception as e:
    print(f'❌ Erro ao importar: {e}')
    sys.exit(1)
" || exit 1
echo ""

# 8. Verifica porta
echo "8. Verificando porta 8080..."
if netstat -an 2>/dev/null | grep -q ":8080.*LISTEN"; then
    echo "⚠️  Porta 8080 já está em uso!"
else
    echo "✅ Porta 8080 livre"
fi
echo ""

# 9. Tenta executar (modo teste)
echo "9. Testando execução (5 segundos)..."
timeout 5 python3 servidor_auto.py 2>&1 &
TEST_PID=$!
sleep 2
if ps -p $TEST_PID > /dev/null 2>&1; then
    echo "✅ Servidor iniciou corretamente!"
    kill $TEST_PID 2>/dev/null
    echo ""
    echo "=== TUDO OK! Pode iniciar com: bash iniciar_auto.sh ==="
else
    echo "❌ Servidor não iniciou. Verifique os erros acima."
    exit 1
fi

