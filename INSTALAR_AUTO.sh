#!/bin/bash
# Instalador para modo automático (tablet 24h)

clear
echo "╔══════════════════════════════════════╗"
echo "║   SERVIDORZINHO - MODO AUTOMÁTICO   ║"
echo "╚══════════════════════════════════════╝"
echo ""

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "📦 Instalando dependências..."
pkg update -y >/dev/null 2>&1 || true

if ! command -v python3 &> /dev/null; then
    echo "🐍 Instalando Python..."
    pkg install -y python >/dev/null 2>&1 || true
fi

echo "📚 Instalando bibliotecas..."
python3 -m pip install --upgrade pip --quiet 2>/dev/null || true
python3 -m pip install tinytuya --quiet 2>/dev/null || true

echo "⚙️  Configurando scripts..."
chmod +x iniciar_auto.sh parar.sh servidor_auto.py 2>/dev/null || true

# Cria script de inicialização automática
mkdir -p ~/.local/bin

cat > ~/.local/bin/servidor-auto << EOFSCRIPT
#!/bin/bash
cd "$SCRIPT_DIR"
bash iniciar_auto.sh
EOFSCRIPT

cat > ~/.local/bin/servidor-parar << EOFSCRIPT
#!/bin/bash
cd "$SCRIPT_DIR"
bash parar.sh
EOFSCRIPT

chmod +x ~/.local/bin/servidor-auto
chmod +x ~/.local/bin/servidor-parar

if ! echo "$PATH" | grep -q "$HOME/.local/bin"; then
    echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.bashrc
    export PATH="$HOME/.local/bin:$PATH"
fi

echo ""
echo "✅ Instalação concluída!"
echo ""
echo "📌 Comandos disponíveis:"
echo "   servidor-auto  -> Inicia em background"
echo "   servidor-parar -> Para o servidor"
echo ""
echo "📋 Ver logs: tail -f $SCRIPT_DIR/servidor.log"
echo ""

