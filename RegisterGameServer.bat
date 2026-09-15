@echo off
title GameServer Registration Console - Brproject
color 0E
cd /d "%~dp0"

echo.
echo ===============================================
echo   GameServer Registration Console
echo ===============================================
echo.
echo Iniciando ferramenta de registro...
echo.

java -Djava.util.logging.config.file=config/console.cfg -cp "libs/*" ext.mods.gsregistering.GameServerRegister

if %errorlevel% neq 0 (
    echo.
    echo ERRO: Falha ao executar GameServerRegister!
    echo Verifique se:
    echo  - O Java esta instalado
    echo  - O arquivo hexid.txt existe em config/
    echo  - As bibliotecas estao na pasta libs/
    echo.
)

pause

