@echo off
REM =============================================================================
REM BrProject — Configurador Autonomo de Rede WSL2 (Mirrored Mode)
REM Habilita conectividade bidirecional transparente entre Windows Host e WSL2
REM para que Login, GameServer e Proxy Netty funcionem via localhost (127.0.0.1)
REM e via IP da rede local (LAN) sem perda de pacotes ou NAT intermediario.
REM =============================================================================

setlocal enabledelayedexpansion

echo ================================================================================
echo              BrProject — Configurador de Rede para WSL2 / Windows Host
echo ================================================================================
echo.

set "WSL_CONFIG=%USERPROFILE%\.wslconfig"
echo [INFO] Verificando arquivo de configuracao: %WSL_CONFIG%

set "HAS_MIRRORED=0"
if exist "%WSL_CONFIG%" (
    findstr /i "networkingMode=mirrored" "%WSL_CONFIG%" >nul 2>&1
    if !errorlevel! equ 0 (
        set "HAS_MIRRORED=1"
    )
)

if "%HAS_MIRRORED%"=="1" (
    echo [OK] O modo de rede espelhado (networkingMode=mirrored) ja esta ATIVO no seu .wslconfig!
    echo [INFO] O Windows Host e o WSL2 ja compartilham a mesma interface de rede e localhost.
    goto :test_connection
)

echo [AVISO] O modo de rede mirrored nao esta ativo no seu .wslconfig.
echo [INFO] Aplicando configuracao recomendada da Microsoft para compartilhamento de portas...
echo.

REM Se nao existir, cria novo. Se existir, adiciona/atualiza.
if not exist "%WSL_CONFIG%" (
    (
        echo [wsl2]
        echo memory=8GB
        echo processors=8
        echo gpuSupport=true
        echo networkingMode=mirrored
        echo dnsTunneling=true
        echo autoProxy=true
        echo firewall=true
    ) > "%WSL_CONFIG%"
) else (
    REM Faz backup
    copy /Y "%WSL_CONFIG%" "%WSL_CONFIG%.bak" >nul 2>&1
    (
        echo.
        echo # --- BrProject: Rede Espelhada para Proxy Netty e Conexao Local ---
        echo networkingMode=mirrored
        echo dnsTunneling=true
        echo autoProxy=true
        echo firewall=true
    ) >> "%WSL_CONFIG%"
)

echo [SUCESSO] Configuracao gravada em %WSL_CONFIG%!
echo.
echo ================================================================================
echo  ATENCAO: Para ativar as novas configuracoes de rede no WSL2, e necessario reiniciar:
echo.
echo    1) Abra um terminal e execute:  wsl --shutdown
echo    2) Inicie novamente seu terminal Linux/WSL.
echo ================================================================================
echo.

:test_connection
echo [INFO] Testando escuta das portas de borda do Proxy Netty (2106 / 7777)...
powershell.exe -NoProfile -Command "Test-NetConnection -ComputerName 127.0.0.1 -Port 2106 -WarningAction SilentlyContinue | Select-Object -Property ComputerName, Port, TcpTestSucceeded"
powershell.exe -NoProfile -Command "Test-NetConnection -ComputerName 127.0.0.1 -Port 7777 -WarningAction SilentlyContinue | Select-Object -Property ComputerName, Port, TcpTestSucceeded"

echo.
echo [CONCLUIDO] Configurador finalizado.
pause
