@echo off
REM Classpath absoluto e ordenado para AppCDS (evita "shared class paths mismatch").
REM Uso: call "%~dp0cache\brproject-classpath.inc.bat" "%~dp0libs"
setlocal EnableDelayedExpansion
set "LIBS=%~f1"
if "%LIBS%"=="" set "LIBS=%~dp0..\libs"
if not exist "%LIBS%\server.jar" (
  echo ERRO: server.jar nao encontrado em %LIBS%
  endlocal & exit /b 1
)
set "CP=%LIBS%\server.jar"
for /f "delims=" %%f in ('dir /b /on "%LIBS%\*.jar" 2^>nul') do (
  if /i not "%%f"=="server.jar" (
    echo %%f | findstr /i /c:".encrypted" /c:"kotlin-stdlib-2.0.0.jar" /c:"kotlin-reflect-2.0.0.jar" /c:"kotlinx-coroutines-core-jvm-1.8.1.jar" >nul
    if errorlevel 1 set "CP=!CP!;%LIBS%\%%f"
  )
)
endlocal & set "BRPROJECT_CP=%CP%"
