@echo off
REM Invalida AppCDS: server.jar atualizado ou modo GC alterado (G1 vs ZGC).
REM Uso: call "%~dp0cache\brproject-cds-check.inc.bat" "cache\brproject_cds.jsa" "..\libs\server.jar" "G1"
setlocal EnableDelayedExpansion
set "CDS=%~1"
set "JAR=%~2"
set "MODE=%~3"
if "%MODE%"=="" set "MODE=G1"
set "META=%~dp1brproject_cds.gc"

if /i "%MODE%"=="ZGC" (
  if exist "%CDS%" (
    del /f /q "%CDS%" 2>nul
    echo [AppCDS] Snapshot removido - heap CDS incompativel com ZGC.
  )
  >"%META%" echo ZGC
  goto :eof
)

if exist "%CDS%" if exist "%META%" (
  set "OLD="
  for /f "usebackq delims=" %%a in ("%META%") do set "OLD=%%a"
  if /i not "!OLD!"=="%MODE%" (
    del /f /q "%CDS%" 2>nul
    echo [AppCDS] Snapshot removido - modo GC alterado de !OLD! para %MODE%.
  )
)
>"%META%" echo %MODE%

if not exist "%CDS%" goto :eof
if not exist "%JAR%" goto :eof
powershell -NoProfile -Command ^
  "if ((Get-Item -LiteralPath '%JAR%').LastWriteTime -gt (Get-Item -LiteralPath '%CDS%').LastWriteTime) { Remove-Item -LiteralPath '%CDS%' -Force; Write-Host '[AppCDS] Snapshot removido - server.jar foi atualizado.' }"
endlocal
