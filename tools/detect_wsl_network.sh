#!/usr/bin/env bash
# =============================================================================
# BrProject — Diagnostico de Rede WSL2 & Windows Host
# Detecta se esta rodando em WSL, identifica IPs de loopback e LAN
# =============================================================================

set -euo pipefail

echo "================================================================================"
echo "              BrProject — Diagnostico de Rede WSL / Linux                       "
echo "================================================================================"
echo

IS_WSL=false
if grep -qi "microsoft" /proc/version 2>/dev/null || grep -qi "wsl" /proc/version 2>/dev/null; then
    IS_WSL=true
fi

if [ "$IS_WSL" = true ]; then
    echo "[INFO] Ambiente detectado: WSL2 (Windows Subsystem for Linux)"
else
    echo "[INFO] Ambiente detectado: Linux Nativo / Container"
fi

# Detecta IPs das interfaces de rede
echo
echo "--- Interfaces de Rede Detectadas ---"
ip -4 addr show | grep -E "inet " | awk '{print "  " $NF ": " $2}'

echo
echo "--- Status das Portas do Servidor e Proxy Netty ---"
for port in 2106 2107 7777 7778 8080 9014; do
    if command -v ss >/dev/null 2>&1; then
        status=$(ss -tulpn 2>/dev/null | grep ":$port " || true)
    elif command -v netstat >/dev/null 2>&1; then
        status=$(netstat -tulpn 2>/dev/null | grep ":$port " || true)
    else
        status=""
    fi
    if [ -n "$status" ]; then
        echo "  Porta $port: [ESCUTANDO]"
    else
        echo "  Porta $port: [INATIVA]"
    fi
done

echo
echo "================================================================================"
echo " Dica de Conexao no Cliente Lineage 2 (l2.ini):"
echo "  - Se o cliente roda no Windows Host e o servidor roda no WSL:"
echo "    ServerAddr=127.0.0.1  (requer networkingMode=mirrored no .wslconfig)"
echo "  - Se o cliente roda em outro PC na LAN:\ be"
echo "    ServerAddr=<IP_DA_SUA_LAN_WINDOWS_OU_WSL>"
echo "================================================================================"
