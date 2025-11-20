#!/bin/bash
# Script de inicialização automática
# Este script será executado quando o Termux abrir

# Aguarda um pouco para garantir que tudo está pronto
sleep 2

# Verifica se já foi executado (evita loops)
if [ -f ~/.servidorzinho_autorun_done ]; then
    exit 0
fi

# Marca como executado
touch ~/.servidorzinho_autorun_done

# Verifica se os arquivos foram copiados
if [ -f ~/servidorzinho/INSTALAR_AUTO.sh ]; then
    cd ~/servidorzinho
    
    # Se não está instalado, instala
    if [ ! -f .installed ]; then
        echo '🚀 Iniciando instalação automática...'
        bash INSTALAR_AUTO.sh && bash iniciar_auto.sh
    else
        # Verifica se dependências estão OK
        if ! python3 -c 'import tinytuya' 2>/dev/null; then
            echo '⚠️  Reinstalando dependências...'
            bash INSTALAR_AUTO.sh
        fi
        
        # Inicia servidor se não estiver rodando
        if [ ! -f servidor.pid ] || ! ps -p $(cat servidor.pid) > /dev/null 2>&1; then
            echo '🚀 Iniciando servidor...'
            bash iniciar_auto.sh
        fi
    fi
fi

