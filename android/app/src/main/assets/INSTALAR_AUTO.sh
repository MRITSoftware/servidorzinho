#!/bin/bash
# Instalador simplificado e robusto para MRIT Server Local
# Versão otimizada para Termux com tratamento de erros melhorado

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

# PASSO 1: Atualizar pacotes
show_step "1/6 - Atualizando pacotes do Termux"
echo "⏳ Isso pode demorar alguns minutos..."
pkg update -y 2>&1 | tail -5 || echo "⚠️  Aviso: Atualização pode ter falhado, continuando..."
echo "✅ Repositórios atualizados"

# PASSO 2: Instalar Python
show_step "2/6 - Verificando Python"
if ! command -v python3 &> /dev/null; then
    echo "📦 Instalando Python..."
    pkg install -y python 2>&1 | tail -10 || {
        echo "❌ Erro ao instalar Python"
        exit 1
    }
fi
echo "✅ Python: $(python3 --version 2>&1)"

# PASSO 3: Instalar dependências de build (essenciais para cryptography)
show_step "3/6 - Instalando ferramentas de compilação"
echo "📦 Isso é ESSENCIAL para instalar cryptography"
echo "📦 Pode demorar 5-10 minutos..."
echo ""

# Lista simplificada e essencial de dependências
BUILD_DEPS="binutils build-essential python-dev libffi-dev openssl-dev clang rust pkg-config"

echo "📦 Instalando dependências de build..."
if pkg install -y $BUILD_DEPS 2>&1 | tail -20; then
    echo "✅ Dependências de build instaladas"
else
    echo "⚠️  Algumas dependências podem ter falhado, tentando instalar individualmente..."
    for dep in $BUILD_DEPS; do
        if ! pkg list-installed 2>/dev/null | grep -q "^$dep "; then
            echo "📦 Instalando $dep..."
            pkg install -y "$dep" 2>&1 | tail -3 || echo "⚠️  $dep pode ter falhado"
        fi
    done
fi

# Garantir que Rust está no PATH
if [ -d "$HOME/.cargo/bin" ]; then
    export PATH="$HOME/.cargo/bin:$PATH"
    if ! grep -q '\.cargo/bin' ~/.bashrc 2>/dev/null; then
        echo 'export PATH="$HOME/.cargo/bin:$PATH"' >> ~/.bashrc
    fi
fi

# Verificar Rust
if command -v rustc &> /dev/null; then
    echo "✅ Rust: $(rustc --version 2>&1 | head -1)"
else
    echo "⚠️  Rust não encontrado, mas continuando..."
fi

# PASSO 4: Atualizar pip
show_step "4/6 - Atualizando pip"
python3 -m pip install --upgrade pip setuptools wheel --quiet 2>&1 | tail -3 || echo "⚠️  Aviso: Atualização do pip pode ter falhado"
echo "✅ pip atualizado"

# PASSO 5: Instalar cryptography (versão simplificada)
show_step "5/6 - Instalando cryptography"
echo "⏳ Isso pode demorar 10-15 minutos (compilando)..."
echo "⏳ Por favor, NÃO feche o Termux durante este processo..."
echo ""

if python3 -c "import cryptography" 2>/dev/null; then
    echo "✅ cryptography já instalado: $(python3 -c 'import cryptography; print(cryptography.__version__)' 2>&1)"
else
    # Garantir Rust no PATH
    if [ -d "$HOME/.cargo/bin" ]; then
        export PATH="$HOME/.cargo/bin:$PATH"
    fi
    
    # Configurar variáveis de ambiente
    export CARGO_BUILD_JOBS=2
    export RUSTFLAGS="-C link-arg=-Wl,-rpath=$PREFIX/lib"
    
    # Tentar instalar cryptography com múltiplas estratégias
    CRYPTO_SUCCESS=false
    
    # Estratégia 1: Tentar wheel pré-compilado (mais rápido)
    echo "📦 Tentativa 1: Procurando wheel pré-compilado..."
    if python3 -m pip install --only-binary :all: cryptography 2>&1 | tail -10; then
        if python3 -c "import cryptography" 2>/dev/null; then
            echo "✅ cryptography instalado (wheel pré-compilado)"
            CRYPTO_SUCCESS=true
        fi
    fi
    
    # Estratégia 2: Compilar versão mais recente
    if [ "$CRYPTO_SUCCESS" = "false" ]; then
        echo ""
        echo "📦 Tentativa 2: Compilando versão mais recente..."
        if python3 -m pip install --no-cache-dir cryptography 2>&1 | tail -20; then
            if python3 -c "import cryptography" 2>/dev/null; then
                echo "✅ cryptography instalado"
                CRYPTO_SUCCESS=true
            fi
        fi
    fi
    
    # Estratégia 3: Versão específica mais estável
    if [ "$CRYPTO_SUCCESS" = "false" ]; then
        echo ""
        echo "📦 Tentativa 3: Instalando versão estável (41.x)..."
        if python3 -m pip install --no-cache-dir "cryptography>=41.0.0,<43.0.0" 2>&1 | tail -20; then
            if python3 -c "import cryptography" 2>/dev/null; then
                echo "✅ cryptography instalado (versão estável)"
                CRYPTO_SUCCESS=true
            fi
        fi
    fi
    
    # Verificar se funcionou
    if [ "$CRYPTO_SUCCESS" = "false" ]; then
        echo ""
        echo "❌ Erro: Não foi possível instalar cryptography"
        echo ""
        echo "🔧 Soluções manuais:"
        echo "1. Verifique se Rust está instalado: rustc --version"
        echo "2. Se não estiver: pkg install rust"
        echo "3. Adicione ao PATH: export PATH=\"\$HOME/.cargo/bin:\$PATH\""
        echo "4. Tente manualmente: pip install cryptography"
        exit 1
    fi
fi

# PASSO 6: Instalar tinytuya
show_step "6/6 - Instalando tinytuya"
if python3 -c "import tinytuya" 2>/dev/null; then
    echo "✅ tinytuya já instalado: $(python3 -c 'import tinytuya; print(tinytuya.__version__)' 2>/dev/null || echo 'versão desconhecida')"
else
    echo "📦 Instalando tinytuya..."
    if python3 -m pip install --no-cache-dir tinytuya 2>&1 | tail -10; then
        if python3 -c "import tinytuya" 2>/dev/null; then
            echo "✅ tinytuya instalado com sucesso"
        else
            echo "❌ Erro: tinytuya instalado mas não pode ser importado"
            exit 1
        fi
    else
        echo "❌ Erro ao instalar tinytuya"
        echo "Tente manualmente: pip install tinytuya"
        exit 1
    fi
fi

# Configurar scripts
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

# Verificação final
echo ""
echo "🔍 Verificando instalação..."
ALL_OK=true

if ! python3 -c "import cryptography" 2>/dev/null; then
    echo "❌ cryptography não está instalado"
    ALL_OK=false
fi

if ! python3 -c "import tinytuya" 2>/dev/null; then
    echo "❌ tinytuya não está instalado"
    ALL_OK=false
fi

if [ "$ALL_OK" = "true" ]; then
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
else
    echo ""
    echo "❌ Instalação incompleta. Algumas dependências falharam."
    echo "Por favor, execute este script novamente."
    exit 1
fi
