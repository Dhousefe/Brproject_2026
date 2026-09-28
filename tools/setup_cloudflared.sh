#!/usr/bin/env bash
# =============================================================================
# BrProject — Cloudflared Provisioner / Installer (Linux / macOS)
# Detecta arquitetura e instala o executavel oficial em bin/cloudflared
# =============================================================================

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

mkdir -p "$ROOT/bin"
TARGET="$ROOT/bin/cloudflared"

OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
ARCH="$(uname -m)"

echo "=== BrProject Cloudflared Provisioner ==="
echo "Sistema: $OS ($ARCH)"
echo "Destino: $TARGET"
echo

DOWNLOAD_URL=""

case "$OS" in
  linux*)
    case "$ARCH" in
      x86_64|amd64)
        DOWNLOAD_URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64"
        ;;
      aarch64|arm64)
        DOWNLOAD_URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64"
        ;;
      armv7*|armhf)
        DOWNLOAD_URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm"
        ;;
      *)
        echo "Erro: Arquitetura Linux desconhecida: $ARCH" >&2
        exit 1
        ;;
    esac
    ;;
  darwin*)
    case "$ARCH" in
      x86_64)
        DOWNLOAD_URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-darwin-amd64"
        ;;
      arm64)
        DOWNLOAD_URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-darwin-arm64"
        ;;
      *)
        echo "Erro: Arquitetura macOS desconhecida: $ARCH" >&2
        exit 1
        ;;
    esac
    ;;
  *)
    echo "Erro: Sistema operacional nao suportado por este script: $OS" >&2
    exit 1
    ;;
esac

echo "Baixando de: $DOWNLOAD_URL"

if command -v curl >/dev/null 2>&1; then
  curl -fsSL "$DOWNLOAD_URL" -o "$TARGET"
elif command -v wget >/dev/null 2>&1; then
  wget -qO "$TARGET" "$DOWNLOAD_URL"
else
  echo "Erro: curl ou wget nao encontrado no sistema." >&2
  exit 1
fi

chmod +x "$TARGET"

echo "Sucesso! Binario instalado e com permissao de execucao:"
ls -lh "$TARGET"
"$TARGET" --version || true
