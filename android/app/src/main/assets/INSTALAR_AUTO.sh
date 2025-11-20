#!/bin/bash
# Instalador para modo automático (tablet 24h)

clear
echo "╔══════════════════════════════════════╗"
echo "║   SERVIDORZINHO - MODO AUTOMÁTICO   ║"
echo "╚══════════════════════════════════════╝"
echo ""

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "📦 Atualizando pacotes..."
pkg update -y >/dev/null 2>&1 || true

if ! command -v python3 &> /dev/null; then
    echo "🐍 Instalando Python..."
    pkg install -y python >/dev/null 2>&1 || true
fi

echo "📚 Instalando bibliotecas Python..."
python3 -m pip install --upgrade pip --quiet 2>/dev/null || true
echo "   Instalando tinytuya..."
python3 -m pip install tinytuya --quiet 2>/dev/null || {
    echo "   ⚠️ Tentando instalação alternativa..."
    pip3 install tinytuya --quiet 2>/dev/null || true
}

# Verifica se instalou
if python3 -c "import tinytuya" 2>/dev/null; then
    echo "   ✅ tinytuya instalado"
else
    echo "   ❌ Erro ao instalar tinytuya"
    echo "   Tente manualmente: pip install tinytuya"
fi

echo "⚙️  Configurando scripts..."
chmod +x iniciar_auto.sh parar.sh servidor_auto.py 2>/dev/null || true

# Cria script de inicialização automática
mkdir -p ~/.local/bin

cat > ~/.local/bin/start << EOFSCRIPT
#!/bin/bash
cd ~/servidorzinho && bash iniciar_auto.sh
EOFSCRIPT

cat > ~/.local/bin/stop << EOFSCRIPT
#!/bin/bash
cd ~/servidorzinho && bash parar.sh
EOFSCRIPT

cat > ~/.local/bin/status << EOFSCRIPT
#!/bin/bash
ps aux | grep servidor_auto | grep -v grep || echo "Servidor não está rodando"
EOFSCRIPT

chmod +x ~/.local/bin/start
chmod +x ~/.local/bin/stop
chmod +x ~/.local/bin/status

if ! echo "$PATH" | grep -q "$HOME/.local/bin"; then
    echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.bashrc
    export PATH="$HOME/.local/bin:$PATH"
fi

echo ""
echo "✅ Instalação concluída!"
echo ""
echo "📌 Comandos rápidos:"
echo "   start   -> Inicia servidor"
echo "   stop    -> Para servidor"
echo "   status  -> Verifica se está rodando"
echo ""
echo "📋 Ver logs: tail -f ~/servidorzinho/servidor.log"
echo ""

