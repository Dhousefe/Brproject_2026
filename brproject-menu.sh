#!/usr/bin/env bash
# =============================================================================
# BrProject — Menu de Compilacao e Gestao de Servico (Linux / macOS)
# Equivalente interativo multiplataforma do brproject-menu.bat
# =============================================================================

set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

# shellcheck source=cache/brproject-java.inc.sh
source "$ROOT/cache/brproject-java.inc.sh"

# Cores ANSI
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
MAGENTA='\033[0;35m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m' # No Color

show_menu() {
    clear 2>/dev/null || true
    echo
    echo -e "${CYAN}${BOLD}================================================================================${NC}"
    echo -e "${CYAN}${BOLD}                          BrProject — Menu de Desenvolvimento                    ${NC}"
    echo -e "${CYAN}${BOLD}================================================================================${NC}"
    echo -e " Java: ${GREEN}$JAVA_CMD${NC} (major=${JAVA_MAJOR:-?})"
    echo -e " CWD:  ${YELLOW}$(pwd)${NC}"
    echo
    echo -e " ${BOLD}[1] Compilar Normal (Rapido)${NC}"
    echo -e "     Compila incrementalmente os arquivos de codigo Java e Kotlin de forma rapida."
    echo
    echo -e " ${BOLD}[2] Clean + Compilar Completo${NC}"
    echo -e "     Exclui todos os artefatos anteriores e realiza compilacao limpa completa."
    echo
    echo -e " ${BOLD}[3] Mount: Compilar + Iniciar Servidores (br-start)${NC}"
    echo -e "     Gera libs/server.jar e inicia os servidores LoginServer e GameServer."
    echo
    echo -e " ${BOLD}[4] Iniciar Proxy Reverso Netty de Borda (:proxy)${NC}"
    echo -e "     Executa o sentinela TCP/HTTP com protecao Fail2Ban e rate limiting."
    echo
    echo -e " ${BOLD}[5] Instalar / Migrar Banco de Dados Autonomo${NC}"
    echo -e "     Executa migracoes DDL para SQLite, MariaDB, MySQL ou PostgreSQL."
    echo
    echo -e " ${BOLD}[0] Sair${NC}"
    echo -e "${CYAN}================================================================================${NC}"
    echo -n " Digite a opcao desejada [0-5]: "
}

while true; do
    show_menu
    read -r opt || exit 0
    echo
    case "$opt" in
        1)
            echo -e "${GREEN}--- Compilando incrementalmente... ---${NC}"
            "$ROOT/gradlew" br-compile
            echo
            echo -e "${CYAN}Pressione [Enter] para voltar ao menu...${NC}"
            read -r _
            ;;
        2)
            echo -e "${YELLOW}--- Executando Clean + Compilacao Completa... ---${NC}"
            "$ROOT/gradlew" br-compile-clean
            echo
            echo -e "${CYAN}Pressione [Enter] para voltar ao menu...${NC}"
            read -r _
            ;;
        3)
            echo -e "${MAGENTA}--- Executando br-start (Compilando e Inicializando)... ---${NC}"
            "$ROOT/gradlew" br-start
            echo
            echo -e "${CYAN}Pressione [Enter] para voltar ao menu...${NC}"
            read -r _
            ;;
        4)
            echo -e "${CYAN}--- Iniciando Proxy Reverso Netty... ---${NC}"
            "$ROOT/StartProxy.sh"
            echo
            echo -e "${CYAN}Pressione [Enter] para voltar ao menu...${NC}"
            read -r _
            ;;
        5)
            echo -e "${GREEN}--- Instalando / Migrando Banco de Dados... ---${NC}"
            "$ROOT/tools/install_db_auto.sh"
            echo
            echo -e "${CYAN}Pressione [Enter] para voltar ao menu...${NC}"
            read -r _
            ;;
        0|q|Q)
            echo "Saindo do BrProject Menu."
            exit 0
            ;;
        *)
            echo -e "${RED}Opcao invalida: '$opt'${NC}"
            sleep 1
            ;;
    esac
done
