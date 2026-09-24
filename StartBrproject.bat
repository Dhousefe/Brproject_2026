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
start "" /B powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0tools\run-hidden.ps1" "Lineage2 NewEra - License Init" "%~f0" "--hidden-child"
exit /b %ERRORLEVEL%

:run_hidden_child
title Lineage2 NewEra - License Init
color 0B

REM --- Habilita cores ANSI no console (cmd.exe) antes de tudo ---
call "%~dp0cache\brproject-ansi.inc.bat"

REM --- Localiza dinamicamente o Java (sem caminhos hardcoded) ---
call "%~dp0cache\brproject-java.inc.bat"

REM --- Verifica presenca de server.jar antes de iniciar (padrao Projeto Start) ---
if not exist "%~dp0libs\server.jar" (
    echo [AVISO] libs\server.jar nao encontrado em %~dp0libs.
    echo [INFO] Compilando o projeto automaticamente via gradlew...
    if exist "%~dp0gradlew.bat" (
        call "%~dp0gradlew.bat" :app-dist:jar --no-daemon
    ) else (
        echo [ERRO] gradlew.bat nao encontrado para compilacao automatica.
        exit /b 1
    )
)

REM --- Classpath deterministico e ordenado para AppCDS (sem wildcard libs/*) ---
if exist "%~dp0cache\brproject-classpath.inc.bat" (
    call "%~dp0cache\brproject-classpath.inc.bat" "%~dp0libs"
) else (
    set "BRPROJECT_CP=%~dp0libs\*"
)

REM ============================================================================
REM OP-1 (Startup Opt): Prepara AppCDS antes do java ser lancado.
REM
REM O snapshot brproject_cds.jsa eh criado uma vez e reusado em todas as
REM inicializacoes subsequentes. Com BRPROJECT_CP estrito (sem wildcard libs/*),
REM a JVM mapeia o snapshot via mmap sem erro de "shared class paths mismatch".
REM ============================================================================
if exist "%~dp0cache\brproject-cds-check.inc.bat" (
    call "%~dp0cache\brproject-cds-check.inc.bat" "%~dp0cache\brproject_cds.jsa" "%~dp0libs\server.jar" "G1"
) else (
    echo [AVISO] brproject-cds-check.inc.bat nao encontrado - AppCDS desabilitado.
)

REM --- Flags de GC Reclaim e Compact Object Headers para Java 25 ---
call "%~dp0cache\brproject-g1-reclaim.inc.bat" 2>nul
set "JVM_EXTRA_FLAGS=-XX:+UseCompactObjectHeaders -XX:+UseStringDeduplication"
if defined G1_RECLAIM_FLAGS set "JVM_EXTRA_FLAGS=%JVM_EXTRA_FLAGS% %G1_RECLAIM_FLAGS%"

REM --- Auth do launcher: DevAuth com token fixo para auto-login silencioso no DashboardPanel ---
set "BRPROJECT_DEV_AUTH=1"
set "BRPROJECT_DEV_TOKEN=brproject-local-dev-2026"

REM ============================================================================
REM Lancamento da JVM com AppCDS estrito e classpath ordenado.
REM ============================================================================
"%JAVA_CMD%" -Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 %JVM_EXTRA_FLAGS% -XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=cache\brproject_cds.jsa -Xlog:cds=error -Dsun.java2d.opengl=true -Dsun.java2d.d3d=true -Dsun.java2d.pmoffscreen=true -Dbrproject.safe.graphics=false -Dbrproject.devAuth=true -cp "%BRPROJECT_CP%" ext.mods.security.LicenseInit %* >> "%~dp0logs\startbrproject.log" 2>&1

exit /b %ERRORLEVEL%
