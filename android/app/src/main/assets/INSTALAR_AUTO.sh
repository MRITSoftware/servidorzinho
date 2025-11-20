#!/bin/bash
# Instalador completo e robusto para MRIT Server Local
# Instala todas as dependências necessárias
# Versão melhorada com tratamento robusto de erros

clear
echo "╔══════════════════════════════════════╗"
echo "║   MRIT Server Local - Instalação    ║"
echo "╚══════════════════════════════════════╝"
echo ""

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Variável para controlar se deve continuar mesmo com erros
CONTINUE_ON_ERROR=true

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

# Função para executar comando com tratamento de erro
safe_execute() {
    local cmd="$1"
    local description="$2"
    
    echo "📦 $description..."
    if eval "$cmd" 2>&1; then
        echo "✅ $description concluído"
        return 0
    else
        if [ "$CONTINUE_ON_ERROR" = "true" ]; then
            echo "⚠️  Aviso: $description falhou, mas continuando..."
            return 1
        else
            echo "❌ Erro: $description falhou"
            exit 1
        fi
    fi
}

# PASSO 1: Atualizar pacotes
show_step "1/7 - Atualizando pacotes do Termux"
echo "⏳ Isso pode demorar alguns minutos..."
pkg update -y 2>&1 | tail -5 || {
    echo "⚠️  Aviso: Atualização de pacotes pode ter falhado, continuando..."
}
echo "✅ Repositórios atualizados"

# PASSO 2: Instalar Python
show_step "2/7 - Verificando Python"
if ! check_command python3; then
    echo "📦 Instalando Python (pode demorar)..."
    pkg install -y python 2>&1 | tail -10 || {
        echo "❌ Erro ao instalar Python"
        exit 1
    }
    echo "✅ Python instalado"
else
    echo "✅ Python: $(python3 --version 2>&1)"
fi

# PASSO 3: Instalar TODAS as dependências de build (CRÍTICO para cryptography)
show_step "3/7 - Instalando ferramentas de compilação"
echo "📦 Isso é ESSENCIAL para instalar cryptography/tinytuya"
echo "📦 Pode demorar 5-10 minutos..."
echo ""

# Lista completa de dependências necessárias para compilar cryptography no Termux
BUILD_DEPS="binutils build-essential python-dev libffi-dev openssl-dev clang rust cargo pkg-config libcrypt-dev"

echo "📦 Instalando dependências de build..."
MISSING_DEPS=""

# Verificar quais dependências estão faltando
for dep in $BUILD_DEPS; do
    if ! pkg list-installed 2>/dev/null | grep -q "^$dep "; then
        MISSING_DEPS="$MISSING_DEPS $dep"
    fi
done

if [ -n "$MISSING_DEPS" ]; then
    echo "📦 Instalando: $MISSING_DEPS"
    echo "⏳ Por favor, aguarde (pode demorar)..."
    
    # Instalar todas de uma vez
    if pkg install -y $MISSING_DEPS 2>&1; then
        echo "✅ Dependências de build instaladas"
    else
        echo "⚠️  Algumas dependências podem ter falhado, tentando instalar individualmente..."
        
        # Tentar instalar individualmente as que falharam
        for dep in $MISSING_DEPS; do
            if ! pkg list-installed 2>/dev/null | grep -q "^$dep "; then
                echo "📦 Instalando $dep..."
                pkg install -y "$dep" 2>&1 | tail -3 || echo "⚠️  $dep pode ter falhado"
            fi
        done
    fi
else
    echo "✅ Todas as dependências de build já estão instaladas"
fi

# Verificar Rust especificamente (crítico para cryptography)
echo ""
echo "🔍 Verificando Rust..."
if command -v rustc &> /dev/null; then
    echo "✅ Rust já instalado: $(rustc --version 2>&1 | head -1)"
    RUST_INSTALLED=true
else
    echo "📦 Rust não encontrado, instalando..."
    
    # Tentar via pkg primeiro
    if pkg install -y rust 2>&1 | tail -10; then
        if command -v rustc &> /dev/null; then
            echo "✅ Rust instalado via pkg"
            RUST_INSTALLED=true
        else
            echo "⚠️  Rust via pkg não funcionou, tentando rustup..."
            RUST_INSTALLED=false
        fi
    else
        echo "⚠️  Instalação via pkg falhou, tentando rustup..."
        RUST_INSTALLED=false
    fi
    
    # Se ainda não tem Rust, tentar rustup
    if [ "$RUST_INSTALLED" = "false" ]; then
        echo "📦 Instalando Rust via rustup..."
        if command -v curl &> /dev/null || pkg install -y curl 2>&1 > /dev/null; then
            curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y 2>&1 | tail -5
            export PATH="$HOME/.cargo/bin:$PATH"
            
            # Adicionar ao PATH permanentemente
            if ! grep -q '\.cargo/bin' ~/.bashrc 2>/dev/null; then
                echo 'export PATH="$HOME/.cargo/bin:$PATH"' >> ~/.bashrc
            fi
            
            if command -v rustc &> /dev/null; then
                echo "✅ Rust instalado via rustup"
                RUST_INSTALLED=true
            else
                echo "❌ Erro: Rust não foi instalado corretamente"
                echo "Tente manualmente:"
                echo "  pkg install rust"
                echo "  ou"
                echo "  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh"
                exit 1
            fi
        else
            echo "❌ Erro: curl não disponível para instalar rustup"
            exit 1
        fi
    fi
fi

# Garantir que Rust está no PATH
if [ -d "$HOME/.cargo/bin" ]; then
    export PATH="$HOME/.cargo/bin:$PATH"
fi

# Verificar se Rust está realmente funcionando
if ! command -v rustc &> /dev/null; then
    echo "❌ Erro crítico: Rust não encontrado após instalação"
    echo "Por favor, instale manualmente:"
    echo "  pkg install rust"
    exit 1
fi

echo "✅ Ferramentas de compilação instaladas e verificadas"

# PASSO 4: Atualizar pip e setuptools
show_step "4/7 - Atualizando pip e ferramentas Python"
python3 -m pip install --upgrade pip setuptools wheel --quiet 2>&1 | tail -3 || {
    echo "⚠️  Aviso: Atualização do pip pode ter falhado, continuando..."
}
echo "✅ pip atualizado"

# PASSO 5: Instalar cryptography (pode demorar)
show_step "5/7 - Instalando cryptography"
echo "⏳ Isso pode demorar 10-15 minutos (compilando)..."
echo "⏳ Por favor, NÃO feche o Termux durante este processo..."
echo ""

if python3 -c "import cryptography" 2>/dev/null; then
    echo "✅ cryptography já instalado: $(python3 -c 'import cryptography; print(cryptography.__version__)' 2>/dev/null)"
else
    # Garantir que Rust está no PATH
    if [ -d "$HOME/.cargo/bin" ]; then
        export PATH="$HOME/.cargo/bin:$PATH"
    fi
    
    # Configurar variáveis de ambiente para compilação
    export CARGO_BUILD_JOBS=2  # Limitar jobs para evitar problemas de memória
    export RUSTFLAGS="-C link-arg=-Wl,-rpath=$PREFIX/lib"
    
    # Tentar instalar cryptography
    echo "📦 Compilando cryptography (isso pode demorar muito)..."
    echo "📦 Por favor, seja paciente..."
    
    CRYPTO_SUCCESS=false
    
    # Tentativa 1: Instalar versão mais recente
    if python3 -m pip install --no-cache-dir --upgrade cryptography 2>&1 | tee /tmp/crypto_install.log; then
        if python3 -c "import cryptography" 2>/dev/null; then
            echo "✅ cryptography instalado com sucesso"
            CRYPTO_SUCCESS=true
        fi
    fi
    
    # Tentativa 2: Se falhou, tentar versão específica mais estável
    if [ "$CRYPTO_SUCCESS" = "false" ]; then
        echo ""
        echo "⚠️  Primeira tentativa falhou, tentando versão alternativa..."
        if python3 -m pip install --no-cache-dir "cryptography>=41.0.0,<43.0.0" 2>&1 | tail -10; then
            if python3 -c "import cryptography" 2>/dev/null; then
                echo "✅ cryptography instalado (versão alternativa)"
                CRYPTO_SUCCESS=true
            fi
        fi
    fi
    
    # Tentativa 3: Versão ainda mais antiga se necessário
    if [ "$CRYPTO_SUCCESS" = "false" ]; then
        echo ""
        echo "⚠️  Tentando versão mais antiga e estável..."
        if python3 -m pip install --no-cache-dir "cryptography>=40.0.0,<42.0.0" 2>&1 | tail -10; then
            if python3 -c "import cryptography" 2>/dev/null; then
                echo "✅ cryptography instalado (versão estável)"
                CRYPTO_SUCCESS=true
            fi
        fi
    fi
    
    # Verificar se finalmente funcionou
    if [ "$CRYPTO_SUCCESS" = "false" ]; then
        echo ""
        echo "❌ Erro: Não foi possível instalar cryptography"
        echo ""
        echo "📋 Informações de diagnóstico:"
        echo "   Rust: $(rustc --version 2>&1 || echo 'NÃO ENCONTRADO')"
        echo "   Python: $(python3 --version 2>&1)"
        echo "   pip: $(python3 -m pip --version 2>&1)"
        echo ""
        echo "🔧 Soluções manuais:"
        echo "1. Verifique se Rust está instalado: rustc --version"
        echo "2. Tente reinstalar Rust: pkg install rust"
        echo "3. Ou instale via rustup: curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh"
        echo "4. Depois tente: pip install cryptography"
        echo ""
        echo "📄 Log completo salvo em: /tmp/crypto_install.log"
        exit 1
    fi
fi

# PASSO 6: Instalar tinytuya
show_step "6/7 - Instalando tinytuya"
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

# PASSO 7: Configurar scripts e comandos
show_step "7/7 - Configurando scripts e comandos"
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
    echo "📋 Ver logs em tempo real:"
    echo "   tail -f ~/servidorzinho/servidor.log"
    echo ""
else
    echo ""
    echo "❌ Instalação incompleta. Algumas dependências falharam."
    echo "Por favor, execute este script novamente ou instale manualmente."
    exit 1
fi
