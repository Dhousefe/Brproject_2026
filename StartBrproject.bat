@echo off
setlocal

REM ============================================================================
REM  StartBrproject.bat — inicia o painel BrProject sem janela de CMD visivel
REM
REM  Fluxo:
REM    1) Quando aberto normalmente, relanca este mesmo .bat via tools\run-hidden.ps1.
REM    2) A instancia oculta entra com a flag interna --hidden-child.
REM    3) LicenseInit roda em background/console oculto, com logs em logs\startbrproject.log.
REM
REM  Nao remova a flag --hidden-child: ela evita recursao infinita.
REM ============================================================================

cd /d "%~dp0"
if not exist "%~dp0logs" mkdir "%~dp0logs"

if /I "%~1"=="--hidden-child" goto run_hidden_child

if not exist "%~dp0tools\run-hidden.ps1" (
    echo [ERRO] tools\run-hidden.ps1 nao encontrado em %~dp0tools
    exit /b 1
)

REM Relanca este StartBrproject em uma janela totalmente oculta e fecha a janela atual.
start "" /B powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0tools\run-hidden.ps1" "Brproject - License Init" "%~f0" "--hidden-child"
exit /b %ERRORLEVEL%

:run_hidden_child
title Brproject - License Init
color 0B

REM --- Habilita cores ANSI no console (cmd.exe) antes de tudo ---
call "%~dp0cache\brproject-ansi.inc.bat"

REM --- Localiza dinamicamente o Java (sem caminhos hardcoded) ---
call "%~dp0cache\brproject-java.inc.bat"

REM --- Auth do launcher: DevAuth com token fixo para auto-login silencioso no DashboardPanel ---
set "BRPROJECT_DEV_AUTH=1"
set "BRPROJECT_DEV_TOKEN=brproject-local-dev-2026"

REM --- Flags para VPS/servidor: evita crash de driver grafico (awt.dll) ---
REM -Dbrproject.safe.graphics=true = molduras e paineis com cores solidas (sem gradiente)
"%JAVA_CMD%" -Xms256m -Xmx512m -Dsun.java2d.opengl=true -Dsun.java2d.d3d=true -Dsun.java2d.pmoffscreen=true -Dbrproject.safe.graphics=false -Dbrproject.devAuth=true -cp "libs/*" ext.mods.security.LicenseInit >> "%~dp0logs\startbrproject.log" 2>&1

exit /b %ERRORLEVEL%
