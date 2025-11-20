#!/bin/bash
# Script de instalação SIMPLES para Termux

set -e

clear
echo "╔══════════════════════════════════════╗"
echo "║   INSTALAÇÃO DO SERVIDORZINHO       ║"
echo "╚══════════════════════════════════════╝"
echo ""

cd ~/servidorzinho 2>/dev/null || {
    echo "❌ Erro: Pasta ~/servidorzinho não encontrada!"
    exit 1
}

echo "📦 Passo 1/5: Atualizando pacotes..."
pkg update -y 2>&1 | tail -3 || echo "⚠️  Aviso"
echo "✅ Concluído"
echo ""

echo "📦 Passo 2/5: Instalando Python e dependências..."
# No Termux, os nomes dos pacotes são diferentes
pkg install -y python rust binutils clang libffi openssl 2>&1 | tail -5 || {
    echo "⚠️  Algumas dependências podem ter falhado"
}
echo "✅ Concluído"
echo ""

# CRÍTICO: Garantir Rust no PATH
if [ -d "$HOME/.cargo/bin" ]; then
    export PATH="$HOME/.cargo/bin:$PATH"
    if ! grep -q '\.cargo/bin' ~/.bashrc 2>/dev/null; then
        echo 'export PATH="$HOME/.cargo/bin:$PATH"' >> ~/.bashrc
    fi
fi

# Verificar Rust
if ! command -v rustc &> /dev/null; then
    echo "⚠️  Rust não encontrado, tentando instalar..."
    if [ -d "$HOME/.cargo/bin" ]; then
        export PATH="$HOME/.cargo/bin:$PATH"
    fi
    # Tentar via rustup se pkg não funcionou
    if ! command -v rustc &> /dev/null; then
        echo "📦 Instalando Rust via rustup..."
        curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y 2>&1 | tail -5
        export PATH="$HOME/.cargo/bin:$PATH"
        if ! grep -q '\.cargo/bin' ~/.bashrc 2>/dev/null; then
            echo 'export PATH="$HOME/.cargo/bin:$PATH"' >> ~/.bashrc
        fi
    fi
fi

if command -v rustc &> /dev/null; then
    echo "✅ Rust: $(rustc --version 2>&1 | head -1)"
else
    echo "⚠️  Rust não instalado, mas continuando..."
fi

echo "📦 Passo 3/5: Atualizando pip..."
python3 -m pip install --upgrade pip setuptools wheel --quiet 2>&1 | tail -2 || true
echo "✅ Concluído"
echo ""

echo "📦 Passo 4/5: Instalando cryptography..."
echo "⏳ Isso pode demorar 10-15 minutos..."
echo ""

# Garantir Rust no PATH antes de tudo
if [ -d "$HOME/.cargo/bin" ]; then
    export PATH="$HOME/.cargo/bin:$PATH"
fi

# Configurar variáveis de ambiente
export CARGO_BUILD_JOBS=1  # Apenas 1 job para evitar problemas de memória
export RUSTFLAGS="-C link-arg=-Wl,-rpath=$PREFIX/lib"

CRYPTO_OK=false

# Tentativa 1: Versão mais antiga e estável (3.4.x)
echo "   Tentativa 1: Versão estável antiga (3.4.x)..."
if python3 -m pip install --no-cache-dir "cryptography>=3.4.8,<3.5.0" 2>&1 | tail -15; then
    if python3 -c "import cryptography" 2>/dev/null; then
        CRYPTO_OK=true
        echo "   ✅ Sucesso com versão 3.4.x!"
    fi
fi

# Tentativa 2: Versão ainda mais antiga (3.3.x)
if [ "$CRYPTO_OK" = "false" ]; then
    echo ""
    echo "   Tentativa 2: Versão mais antiga (3.3.x)..."
    if python3 -m pip install --no-cache-dir "cryptography>=3.3.2,<3.4.0" 2>&1 | tail -15; then
        if python3 -c "import cryptography" 2>/dev/null; then
            CRYPTO_OK=true
            echo "   ✅ Sucesso com versão 3.3.x!"
        fi
    fi
fi

# Tentativa 3: Versão muito antiga (3.0.x) - última tentativa
if [ "$CRYPTO_OK" = "false" ]; then
    echo ""
    echo "   Tentativa 3: Versão muito antiga (3.0.x)..."
    if python3 -m pip install --no-cache-dir "cryptography>=3.0.0,<3.1.0" 2>&1 | tail -15; then
        if python3 -c "import cryptography" 2>/dev/null; then
            CRYPTO_OK=true
            echo "   ✅ Sucesso com versão 3.0.x!"
        fi
    fi
fi

if [ "$CRYPTO_OK" = "false" ]; then
    echo ""
    echo "❌ Erro: Não foi possível instalar cryptography"
    echo ""
    echo "🔧 Soluções manuais:"
    echo "1. Verifique Rust: rustc --version"
    echo "2. Se não tiver: pkg install rust"
    echo "3. Adicione ao PATH: export PATH=\"\$HOME/.cargo/bin:\$PATH\""
    echo "4. Tente: pip install cryptography==3.4.8"
    exit 1
fi

echo "✅ Concluído"
echo ""

echo "📦 Passo 5/5: Instalando tinytuya..."
if python3 -m pip install tinytuya 2>&1 | tail -5; then
    if python3 -c "import tinytuya" 2>/dev/null; then
        echo "✅ tinytuya instalado"
    else
        echo "❌ Erro: tinytuya não pode ser importado"
        exit 1
    fi
else
    echo "❌ Erro ao instalar tinytuya"
    exit 1
fi

touch .installed

echo ""
echo "╔══════════════════════════════════════╗"
echo "║   ✅ INSTALAÇÃO CONCLUÍDA!          ║"
echo "╚══════════════════════════════════════╝"
echo ""
echo "🚀 Para iniciar o servidor:"
echo "   cd ~/servidorzinho"
echo "   bash iniciar_auto.sh"
echo ""
