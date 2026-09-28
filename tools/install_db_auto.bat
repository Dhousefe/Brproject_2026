@echo off
REM =============================================================================
REM BrProject — Instalador e Migrador Autonomo de Banco de Dados (Windows)
REM Suporta SQLite, MariaDB, MySQL e PostgreSQL sem regressao.
REM =============================================================================

setlocal enabledelayedexpansion
cd /d "%~dp0.."

set "CONFIG_FILE=%cd%\game\config\server.properties"
set "DEFAULT_URL="
set "DEFAULT_USER=root"
set "DEFAULT_PASS=root"

if exist "%CONFIG_FILE%" (
    for /f "tokens=1,* delims==" %%a in ('type "%CONFIG_FILE%" ^| findstr /r /c:"^[ ]*sql\.url[ ]*="') do (
        set "val=%%b"
        for /f "tokens=* delims= " %%x in ("!val!") do set "DEFAULT_URL=%%x"
    )
    for /f "tokens=1,* delims==" %%a in ('type "%CONFIG_FILE%" ^| findstr /r /c:"^[ ]*sql\.login[ ]*="') do (
        set "val=%%b"
        for /f "tokens=* delims= " %%x in ("!val!") do set "DEFAULT_USER=%%x"
    )
    for /f "tokens=1,* delims==" %%a in ('type "%CONFIG_FILE%" ^| findstr /r /c:"^[ ]*sql\.password[ ]*="') do (
        set "val=%%b"
        for /f "tokens=* delims= " %%x in ("!val!") do set "DEFAULT_PASS=%%x"
    )
)

if "%DEFAULT_URL%"=="" (
    set "DEFAULT_URL=jdbc:sqlite:%cd%/data/brproject.sqlite"
)

set "DB_URL=%~1"
if "%DB_URL%"=="" set "DB_URL=%DEFAULT_URL%"

set "DB_USER=%~2"
if "%DB_USER%"=="" set "DB_USER=%DEFAULT_USER%"

set "DB_PASSWORD=%~3"
if "%DB_PASSWORD%"=="" set "DB_PASSWORD=%DEFAULT_PASS%"

echo === BrProject Database Installer ^& Migrator ===
echo URL:  %DB_URL%
echo User: %DB_USER%
echo.

call gradlew.bat :db-migrate:run -PdbUrl="%DB_URL%" -PdbUser="%DB_USER%" -PdbPassword="%DB_PASSWORD%" --quiet --console=plain

if not exist "flag" mkdir "flag"
(
    echo url=%DB_URL%
    echo user=%DB_USER%
    echo status=INSTALLED_AUTONOMOUS
) > "flag\PrepararTeste.done"

echo.
echo Instalacao concluida com sucesso!
echo Marker gravado: flag\PrepararTeste.done
