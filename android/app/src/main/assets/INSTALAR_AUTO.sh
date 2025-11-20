#!/bin/bash
# Instalador completo e robusto para MRIT Server Local
# Instala todas as dependências necessárias

set -e  # Para em caso de erro crítico

clear
echo "╔══════════════════════════════════════╗"
echo "║   MRIT Server Local - Instalação    ║"
echo "╚══════════════════════════════════════╝"
echo ""

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Função para mostrar progresso
show_step() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "📌 $1"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# Função para verificar se comando existe
check_command() {
    if command -v "$1" &> /dev/null; then
        echo "✅ $1 já instalado"
        return 0
    else
        return 1
    fi
}

# PASSO 1: Atualizar pacotes
show_step "1/6 - Atualizando pacotes do Termux"
pkg update -y 2>&1 | grep -v "^$" | tail -3 || true
echo "✅ Pacotes atualizados"

# PASSO 2: Instalar Python
show_step "2/6 - Verificando Python"
if ! check_command python3; then
    echo "📦 Instalando Python (pode demorar)..."
    pkg install -y python 2>&1 | grep -E "(Setting up|Unpacking|done)" || true
    echo "✅ Python instalado"
else
    echo "✅ Python: $(python3 --version)"
fi

# PASSO 3: Instalar dependências de build (CRÍTICO para cryptography)
show_step "3/6 - Instalando ferramentas de compilação"
echo "📦 Isso é necessário para instalar cryptography/tinytuya"
echo "📦 Pode demorar alguns minutos..."

BUILD_DEPS="rust binutils build-essential python-dev libffi-dev openssl-dev clang"
MISSING_DEPS=""

for dep in $BUILD_DEPS; do
    if ! pkg list-installed 2>/dev/null | grep -q "^$dep "; then
        MISSING_DEPS="$MISSING_DEPS $dep"
    fi
done

if [ -n "$MISSING_DEPS" ]; then
    echo "📦 Instalando: $MISSING_DEPS"
    pkg install -y $MISSING_DEPS 2>&1 | grep -E "(Setting up|Unpacking|done)" || {
        echo "⚠️ Algumas dependências podem ter falhado, continuando..."
    }
    echo "✅ Ferramentas de compilação instaladas"
else
    echo "✅ Todas as ferramentas já instaladas"
fi

# PASSO 4: Atualizar pip
show_step "4/6 - Atualizando pip"
python3 -m pip install --upgrade pip --quiet 2>&1 | tail -1 || true
echo "✅ pip atualizado"

# PASSO 5: Instalar cryptography (pode demorar)
show_step "5/6 - Instalando cryptography"
echo "⏳ Isso pode demorar 5-10 minutos (compilando)..."
echo "⏳ Por favor, aguarde..."

if python3 -c "import cryptography" 2>/dev/null; then
    echo "✅ cryptography já instalado"
else
    python3 -m pip install --no-cache-dir cryptography 2>&1 | while IFS= read -r line; do
        # Mostra apenas linhas importantes
        if echo "$line" | grep -qE "(Collecting|Installing|Building|Successfully|ERROR|error)"; then
            echo "   $line"
        fi
    done
    
    if python3 -c "import cryptography" 2>/dev/null; then
        echo "✅ cryptography instalado com sucesso"
    else
        echo "❌ Erro ao instalar cryptography"
        echo "Tente manualmente: pip install cryptography"
        exit 1
    fi
fi

# PASSO 6: Instalar tinytuya
show_step "6/6 - Instalando tinytuya"
if python3 -c "import tinytuya" 2>/dev/null; then
    echo "✅ tinytuya já instalado"
else
    echo "📦 Instalando tinytuya..."
    python3 -m pip install --no-cache-dir tinytuya 2>&1 | while IFS= read -r line; do
        if echo "$line" | grep -qE "(Collecting|Installing|Successfully|ERROR|error)"; then
            echo "   $line"
        fi
    done
    
    if python3 -c "import tinytuya" 2>/dev/null; then
        echo "✅ tinytuya instalado com sucesso"
    else
        echo "❌ Erro ao instalar tinytuya"
        echo "Tente manualmente: pip install tinytuya"
        exit 1
    fi
fi

# Configurar scripts
show_step "Configurando scripts"
chmod +x iniciar_auto.sh parar.sh servidor_auto.py testar_servidor.sh 2>/dev/null || true

# Criar comandos rápidos
mkdir -p ~/.local/bin

cat > ~/.local/bin/start << 'EOFSCRIPT'
#!/bin/bash
cd ~/servidorzinho && bash iniciar_auto.sh
EOFSCRIPT

cat > ~/.local/bin/stop << 'EOFSCRIPT'
#!/bin/bash
cd ~/servidorzinho && bash parar.sh
EOFSCRIPT

cat > ~/.local/bin/status << 'EOFSCRIPT'
#!/bin/bash
cd ~/servidorzinho
if ps aux | grep servidor_auto | grep -v grep > /dev/null; then
    echo "✅ Servidor está rodando"
    ps aux | grep servidor_auto | grep -v grep
else
    echo "❌ Servidor não está rodando"
fi
EOFSCRIPT

cat > ~/.local/bin/logs << 'EOFSCRIPT'
#!/bin/bash
cd ~/servidorzinho
if [ -f servidor.log ]; then
    tail -30 servidor.log
else
    echo "Log não encontrado"
fi
EOFSCRIPT

chmod +x ~/.local/bin/start
chmod +x ~/.local/bin/stop
chmod +x ~/.local/bin/status
chmod +x ~/.local/bin/logs

if ! echo "$PATH" | grep -q "$HOME/.local/bin"; then
    echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.bashrc
    export PATH="$HOME/.local/bin:$PATH"
fi

# Marcar como instalado
touch .installed

echo ""
echo "╔══════════════════════════════════════╗"
echo "║   ✅ INSTALAÇÃO CONCLUÍDA!          ║"
echo "╚══════════════════════════════════════╝"
echo ""
echo "📌 Comandos disponíveis:"
echo "   start   -> Inicia o servidor"
echo "   stop    -> Para o servidor"
echo "   status  -> Verifica se está rodando"
echo "   logs    -> Mostra os últimos logs"
echo ""
echo "🚀 Para iniciar o servidor agora:"
echo "   bash iniciar_auto.sh"
echo ""
echo "📋 Ver logs em tempo real:"
echo "   tail -f ~/servidorzinho/servidor.log"
echo ""
