#!/bin/bash
# Script para disparar o workflow de build do APK no GitHub
# Requer token do GitHub com permissão para workflows

TOKEN="${1:-}"
OWNER="${2:-}"
REPO="${3:-}"

# Se não foram fornecidos, tenta pegar do git remoto
if [ -z "$OWNER" ] || [ -z "$REPO" ]; then
    REMOTE_URL=$(git remote get-url origin 2>/dev/null)
    if [ -n "$REMOTE_URL" ]; then
        if echo "$REMOTE_URL" | grep -qE "github\.com[:/]([^/]+)/([^/]+)"; then
            OWNER=$(echo "$REMOTE_URL" | sed -E 's/.*github\.com[:/]([^/]+)\/([^/]+).*/\1/')
            REPO=$(echo "$REMOTE_URL" | sed -E 's/.*github\.com[:/]([^/]+)\/([^/]+).*/\2/' | sed 's/\.git$//')
            echo "✅ Detectado: $OWNER/$REPO"
        fi
    fi
fi

if [ -z "$OWNER" ] || [ -z "$REPO" ]; then
    echo "❌ Erro: Não foi possível detectar Owner/Repo"
    echo "Uso: ./disparar_workflow.sh SEU_TOKEN [OWNER] [REPO]"
    exit 1
fi

if [ -z "$TOKEN" ]; then
    echo "⚠️  Token do GitHub não fornecido"
    echo "Você pode:"
    echo "1. Criar um token em: https://github.com/settings/tokens"
    echo "2. Dar permissão 'workflow' ao token"
    echo "3. Executar: ./disparar_workflow.sh SEU_TOKEN"
    echo ""
    echo "Ou faça manualmente:"
    echo "1. Vá para: https://github.com/$OWNER/$REPO/actions"
    echo "2. Clique em 'Build APK'"
    echo "3. Clique em 'Run workflow'"
    exit 0
fi

echo "🚀 Disparando workflow 'Build APK'..."

URL="https://api.github.com/repos/$OWNER/$REPO/actions/workflows/build-apk.yml/dispatches"

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    -H "Accept: application/vnd.github.v3+json" \
    -H "Authorization: token $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"ref":"main"}' \
    "$URL")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "204" ]; then
    echo "✅ Workflow disparado com sucesso!"
    echo "📱 Acompanhe em: https://github.com/$OWNER/$REPO/actions"
else
    echo "❌ Erro ao disparar workflow (HTTP $HTTP_CODE):"
    echo "$BODY" | jq -r '.message' 2>/dev/null || echo "$BODY"
    exit 1
fi

