@echo off
setlocal
cd /d "%~dp0"
title Brproject - Build

:menu
cls
echo.
echo   ========================================================================================================
echo                                          Brproject - Build
echo.
echo   [1] Compila incrementalmente os arquivos de codigo Java e Kotlin de forma rapida.
echo   [2] Exclui todos os artefatos de compilacao anteriores, em seguida, realiza uma compilacao completa.
echo   [3] Esta e uma acao composta que automatiza o ciclo completo para iniciar o servidor de desenvolvimento
echo.
echo   ========================================================================================================
echo.
echo.
echo.
echo   [1] Compilar normal (Rapido)
echo   [2] Clean + Compilar normal
echo   [3] Mount: Mount e Teste
echo   [0] Sair
echo.
rem choice: teclas 1, 2, 3, 0  -  ERRORLEVEL 1..4 (testar do maior para o menor)
choice /C 1230 /N /M "Digite a opcao: "

if errorlevel 4 goto :sair
if errorlevel 3 goto :ant_dist
if errorlevel 2 goto :clean_build
if errorlevel 1 goto :compile

:compile
echo.
echo --- Compilando... ---
call "%~dp0gradlew.bat" br-compile
if errorlevel 1 (
    echo.
    echo [AVISO] A compilacao falhou. StartBrproject.bat nao sera iniciado.
    goto :apos
)
if exist "%~dp0StartBrproject.bat" (
    echo.
    echo --- Iniciando StartBrproject.bat... ---
    call "%~dp0StartBrproject.bat"
) else (
    echo.
    echo [ERRO] StartBrproject.bat nao encontrado em %~dp0
)
goto :apos

:clean_build
echo.
echo --- Clean + compilar... ---
call "%~dp0gradlew.bat" br-compile-clean
goto :apos

:ant_dist
echo.
echo --- Ant dist-test... ---
call "%~dp0gradlew.bat" br-start
goto :apos

:apos
echo.
pause
goto :menu

:sair
echo.
endlocal
exit /b 0
