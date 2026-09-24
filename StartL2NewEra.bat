@echo off
setlocal
title Lineage2 NewEra - Local Launcher
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0StartL2NewEra.ps1"
if errorlevel 1 (
    echo.
    echo [ERRO] A inicializacao falhou. Consulte a pasta logs.
    pause
)
endlocal
