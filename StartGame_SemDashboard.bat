@echo off
title Brproject - Game
color 0B
setlocal

REM ===== Servidor ocultado em background pelo tools\run-hidden.ps1 =====
REM Chamado pelo gradlew.bat quando o usuario pede br-start / br-ant-dist-test.
REM A janela de CMD NAO e exibida (WindowStyle=Hidden via Process.StartInfo).
REM Saida do Java/Ktor redirecionada para logs\game-server.log para manter
REM observabilidade mesmo sem janela.
if not exist "%~dp0logs" mkdir "%~dp0logs"

REM ===== Inicializador sem dashboard elaborado By Eduardo.SilvaL2J =====
REM Primeira execucao: se ainda existir .example e flag/PrepararTeste.done nao existir,
REM abre o painel Preparar Ambiente uma unica vez (via task Gradle PrepararTeste),
REM aplica migrations/hexid/configs e entao continua com o GameServer.
cd /d "%~dp0"
call :ensure_first_run_prepared
if errorlevel 1 goto fail

REM --- Habilita cores ANSI no console (cmd.exe) antes do Java imprimir o banner ---
call "%~dp0cache\brproject-ansi.inc.bat"

call "%~dp0cache\brproject-java.inc.bat"

REM ===== JVM FLAGS: G1GC + AppCDS + reclaim periodico (similar ao ZGC) =====
call "%~dp0cache\brproject-g1-reclaim.inc.bat"
set JVM_FLAGS=-Xms3g -Xmx3g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:G1HeapRegionSize=16m -XX:+UseStringDeduplication -XX:+UseCompressedOops -XX:+UseCompactObjectHeaders -XX:+TieredCompilation -XX:TieredStopAtLevel=4 %G1_RECLAIM_FLAGS% -XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=cache/brproject_cds.jsa -Xlog:cds=error

cd /d "%~dp0game"

if not exist cache mkdir cache

call "%~dp0cache\brproject-cds-check.inc.bat" "cache\brproject_cds.jsa" "%~dp0libs\server.jar" "G1"
call "%~dp0cache\brproject-classpath.inc.bat" "%~dp0libs"

REM --- Redireciona stdout+stderr para o log do servidor (janela oculta, logs preservados) ---
"%JAVA_CMD%" %JVM_FLAGS% -cp "%BRPROJECT_CP%" ext.mods.gameserver.GameServer >> "%~dp0logs\game-server.log" 2>&1
goto end_of_script

:end_of_script
exit /b 0

:ensure_first_run_prepared
if exist "%~dp0flag\PrepararTeste.done" exit /b 0

set "HAS_EXAMPLE=0"
if exist "%~dp0game\config\server.properties.example" set "HAS_EXAMPLE=1"
if exist "%~dp0game\configs\server.properties.example" set "HAS_EXAMPLE=1"
if "%HAS_EXAMPLE%"=="0" exit /b 0

echo.
echo [BrProject] Primeira execucao detectada.
echo [BrProject] Abrindo painel Preparar Ambiente para configurar IP, banco, migrations e hexid...
echo.
if not exist "%~dp0gradlew.bat" (
    echo [ERRO] gradlew.bat nao encontrado em %~dp0
    exit /b 1
)
call "%~dp0gradlew.bat" PrepararTeste --offline --no-daemon
if errorlevel 1 (
    echo.
    echo [ERRO] PrepararTeste falhou. Corrija o erro acima antes de iniciar o GameServer.
    exit /b 1
)
exit /b 0

:fail
echo.
echo [ERRO] StartGame_SemDashboard.bat interrompido.
exit /b 1
