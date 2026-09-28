@echo off
REM Brproject — Netty Reverse Proxy (Windows)
REM Inicializa o proxy de borda TCP / HTTP com suporte a Fail2Ban e rate limiting.

setlocal enabledelayedexpansion
cd /d "%~dp0"

echo === Brproject Netty Reverse Proxy ===
echo CWD: %cd%
echo.

call gradlew.bat :proxy:run --quiet --console=plain %*
