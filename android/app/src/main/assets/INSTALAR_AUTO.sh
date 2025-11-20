#!/bin/bash
# Script de instalação ULTRA SIMPLES - foca em fazer funcionar

set -e  # Para em caso de erro

clear
echo "╔══════════════════════════════════════╗"
echo "║   INSTALAÇÃO DO SERVIDORZINHO       ║"
echo "╚══════════════════════════════════════╝"
echo ""

# Ir para o diretório correto
cd ~/servidorzinho 2>/dev/null || {
    echo "❌ Erro: Pasta ~/servidorzinho não encontrada!"
    exit 1
}

echo "📦 Passo 1/4: Atualizando pacotes..."
pkg update -y 2>&1 | tail -3 || echo "⚠️  Aviso: Atualização pode ter falhado"
echo "✅ Concluído"
echo ""

echo "📦 Passo 2/4: Instalando Python e dependências..."
pkg install -y python rust binutils build-essential libffi-dev openssl-dev clang 2>&1 | tail -5 || {
    echo "⚠️  Algumas dependências podem ter falhado, continuando..."
}
echo "✅ Concluído"
echo ""

# Garantir Rust no PATH
if [ -d "$HOME/.cargo/bin" ]; then
    export PATH="$HOME/.cargo/bin:$PATH"
    if ! grep -q '\.cargo/bin' ~/.bashrc 2>/dev/null; then
        echo 'export PATH="$HOME/.cargo/bin:$PATH"' >> ~/.bashrc
    fi
fi

echo "📦 Passo 3/4: Instalando cryptography..."
python3 -m pip install --upgrade pip setuptools wheel --quiet 2>&1 | tail -2 || true

# Tentar instalar cryptography - múltiplas tentativas
CRYPTO_OK=false

# Tentativa 1: Wheel pré-compilado
echo "   Tentativa 1: Wheel pré-compilado..."
if python3 -m pip install --only-binary :all: cryptography 2>&1 | tail -5; then
    if python3 -c "import cryptography" 2>/dev/null; then
        CRYPTO_OK=true
        echo "   ✅ Sucesso!"
    fi
fi

# Tentativa 2: Compilar
if [ "$CRYPTO_OK" = "false" ]; then
    echo "   Tentativa 2: Compilando (pode demorar 10-15 min)..."
    if python3 -m pip install --no-cache-dir cryptography 2>&1 | tail -10; then
        if python3 -c "import cryptography" 2>/dev/null; then
            CRYPTO_OK=true
            echo "   ✅ Sucesso!"
        fi
    fi
fi

# Tentativa 3: Versão específica
if [ "$CRYPTO_OK" = "false" ]; then
    echo "   Tentativa 3: Versão estável (41.x)..."
    if python3 -m pip install --no-cache-dir "cryptography>=41.0.0,<43.0.0" 2>&1 | tail -10; then
        if python3 -c "import cryptography" 2>/dev/null; then
            CRYPTO_OK=true
            echo "   ✅ Sucesso!"
        fi
    fi
fi

if [ "$CRYPTO_OK" = "false" ]; then
    echo "❌ Erro: Não foi possível instalar cryptography"
    echo "Tente manualmente: pip install cryptography"
    exit 1
fi

echo "✅ Concluído"
echo ""

echo "📦 Passo 4/4: Instalando tinytuya..."
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

# Marcar como instalado
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
