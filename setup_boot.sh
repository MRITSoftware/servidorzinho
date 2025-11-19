#!/bin/bash
# Configura o Termux para iniciar o servidor automaticamente

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "⚙️  Configurando inicialização automática..."

# Cria diretório de boot scripts se não existir
mkdir -p ~/.termux/boot

# Cria script de inicialização
cat > ~/.termux/boot/servidorzinho.sh << EOFSCRIPT
#!/bin/bash
# Inicia o servidorzinho automaticamente após boot

sleep 10  # Aguarda 10 segundos após o boot

cd "$SCRIPT_DIR"

# Verifica se já está rodando
if [ -f "servidor.pid" ]; then
    OLD_PID=\$(cat servidor.pid)
    if ps -p "\$OLD_PID" > /dev/null 2>&1; then
        exit 0
    fi
    rm -f servidor.pid
fi

# Inicia o servidor em background
bash iniciar_auto.sh > /dev/null 2>&1
EOFSCRIPT

chmod +x ~/.termux/boot/servidorzinho.sh

# Adiciona ao .bashrc para iniciar quando Termux abrir
if ! grep -q "servidorzinho" ~/.bashrc; then
    cat >> ~/.bashrc << 'EOFBASHRC'

# Auto-inicia servidorzinho
if [ -f "$HOME/servidorzinho/servidor.pid" ]; then
    OLD_PID=$(cat "$HOME/servidorzinho/servidor.pid")
    if ! ps -p "$OLD_PID" > /dev/null 2>&1; then
        cd "$HOME/servidorzinho" && bash iniciar_auto.sh > /dev/null 2>&1 &
    fi
else
    cd "$HOME/servidorzinho" 2>/dev/null && bash iniciar_auto.sh > /dev/null 2>&1 &
fi
EOFBASHRC
fi

echo "✅ Configurado!"
echo ""
echo "📌 O servidor iniciará automaticamente quando:"
echo "   - O Termux for aberto"
echo "   - O tablet for reiniciado"
echo ""
echo "💡 Para desabilitar:"
echo "   1. Remova as linhas do ~/.bashrc"
echo "   2. Delete: ~/.termux/boot/servidorzinho.sh"

