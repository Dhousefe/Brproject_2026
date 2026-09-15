@echo off
setlocal enabledelayedexpansion
title BrProject-2026 - Configurador de Redirecionamento TCP (Cliente LAN)

echo =========================================================================
echo   BrProject-2026: Configurador de PortProxy TCP para Cliente na Rede Local
echo =========================================================================
echo.
echo Este script configura o redirecionamento local (netsh portproxy) neste PC,
echo permitindo que o cliente Lineage 2 aponte para 127.0.0.1 no l2.ini
echo e seja automaticamente tunelado pela rede local ate a maquina do Servidor.
echo.

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERRO] Este script precisa ser executado como Administrador!
    echo Clique com o botao direito e selecione "Executar como Administrador".
    echo.
    pause
    exit /b 1
)

set SERVER_IP=%1
if "%SERVER_IP%"=="" (
    set /p SERVER_IP="Digite o IP do Computador do Servidor na Rede Local (ex: 192.168.1.100): "
)

if "%SERVER_IP%"=="" (
    echo [ERRO] IP do Servidor nao informado. Operacao cancelada.
    pause
    exit /b 1
)

echo.
echo [*] Configurando redirecionamento para o Servidor em: %SERVER_IP% ...

netsh interface portproxy delete v4tov4 listenport=2106 listenaddress=127.0.0.1 >nul 2>&1
netsh interface portproxy delete v4tov4 listenport=7777 listenaddress=127.0.0.1 >nul 2>&1

netsh interface portproxy add v4tov4 listenport=2106 listenaddress=127.0.0.1 connectport=2106 connectaddress=%SERVER_IP%
if %errorlevel% equ 0 (
    echo   [OK] Porta 2106 (Login): 127.0.0.1:2106 -^> %SERVER_IP%:2106
) else (
    echo   [FALHA] Nao foi possivel configurar porta 2106.
)

netsh interface portproxy add v4tov4 listenport=7777 listenaddress=127.0.0.1 connectport=7777 connectaddress=%SERVER_IP%
if %errorlevel% equ 0 (
    echo   [OK] Porta 7777 (Game):  127.0.0.1:7777 -^> %SERVER_IP%:7777
) else (
    echo   [FALHA] Nao foi possivel configurar porta 7777.
)

echo.
echo [*] Regras de PortProxy ativas no Windows:
netsh interface portproxy show all

echo.
echo =========================================================================
echo   Sucesso! Agora o cliente Lineage 2 pode usar 127.0.0.1 no l2.ini
echo   neste computador e conectara perfeitamente no servidor %SERVER_IP%.
echo =========================================================================
echo.
pause
