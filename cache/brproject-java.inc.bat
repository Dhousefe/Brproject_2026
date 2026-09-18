@echo off
REM ============================================================================
REM  Brproject - Localizador dinamico do Java (sem caminhos hardcoded)
REM
REM  Ordem de deteccao:
REM    1. JAVA_HOME ja definido no ambiente (Usuario / Sistema)
REM    2. Diretorios comuns de instalacao do JDK (Program Files, etc.)
REM    3. Comando `where java` (varre o PATH)
REM    4. Fallback: comando global `java` (assume PATH configurado)
REM
REM  Suporta qualquer versao do JDK (17, 21, 25, ...) e qualquer vendor
REM  (Eclipse Adoptium, Zulu, Microsoft, Oracle, Corretto, Liberica, ...).
REM
REM  Ao final desta rotina, a variavel %JAVA_CMD% estara pronta para uso.
REM  Nenhum caminho fica fixo no script - edite apenas se quiser forcar uma
REM  versao especifica atraves da variavel BRPROJECT_JAVA_HOME.
REM ============================================================================

REM --- Permite ao usuario forcar um caminho especifico sobrescrevendo tudo ---
if not "%BRPROJECT_JAVA_HOME%"=="" (
    if exist "%BRPROJECT_JAVA_HOME%\bin\java.exe" (
        set "JAVA_HOME=%BRPROJECT_JAVA_HOME%"
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
        goto :java_found
    )
)

REM --- 1) JAVA_HOME vindo do ambiente ---
REM OP-3 (Startup Opt): quando %JAVA_HOME% (ou %BRPROJECT_JAVA_HOME% acima)
REM ja aponta para um JDK valido, vamos DIRETO para :java_found sem executar
REM a varredura de 12 diretorios comuns abaixo (100-400ms tipicos em NVMe).
REM Se JAVA_HOME existe mas eh invalido (path deletado ou string lixo do tipo
REM ":JAVA_HOME"), pulamos a secao 2 (varredura lenta) e vamos direto para a
REM secao 3 ('where java'), que eh muito mais rapida.
if defined JAVA_HOME (
    call :normalize_java_home
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
        goto :java_found
    )
    if exist "%JAVA_HOME%\bin\javaw.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\javaw.exe"
        goto :java_found
    )
    REM OP-3: JAVA_HOME definido mas invalido. Nao desce para a varredura
    REM completa de diretorios (secao 2) — pula direto para 'where java'.
    goto :try_where
)

REM --- 1.5) JDK 25 Autonomo Local (Projeto Start / Gradle Toolchain) ---
if not defined JAVA_CMD (
    for %%D in ("%~dp0..\gradle\jdk25" "%~dp0gradle\jdk25" "D:\Projeto_Start_Brproject\gradle\jdk25" "D:\graalvm25") do (
        if not defined JAVA_CMD (
            if exist "%%~fD\bin\java.exe" (
                set "JAVA_HOME=%%~fD"
                set "JAVA_CMD=%%~fD\bin\java.exe"
                goto :java_found
            )
        )
    )
)

REM --- 2) Diretorios comuns de instalacao ---
set "_JDK_GLOBS=C:\Program Files\Eclipse Adoptium\jdk-2*;C:\Program Files\Java\jdk-*;C:\Program Files\Microsoft\jdk-*;C:\Program Files\Zulu\zulu-*;C:\Program Files\Amazon Corretto\jdk*;C:\Program Files\BellSoft\LibericaJDK-*;C:\Program Files\OpenJDK\jdk-*;C:\Program Files\OpenLogic\jdk-*;C:\Program Files\ojdkbuild\java-*;C:\Program Files\Common Files\ojdkbuild\java-*;C:\Program Files (x86)\Eclipse Adoptium\jdk-2*;C:\Program Files (x86)\Java\jdk-*;C:\jdk-2*;C:\jdk-*"
for %%P in ("%_JDK_GLOBS:;=" "%") do (
    if not defined JAVA_CMD (
        for /d %%D in (%%~P) do (
            if not defined JAVA_CMD (
                if exist "%%~fD\bin\java.exe" (
                    set "JAVA_HOME=%%~fD"
                    set "JAVA_CMD=%%~fD\bin\java.exe"
                )
            )
        )
    )
)
if defined JAVA_CMD goto :java_found

REM --- 3) varre o PATH via `where` ---
:try_where
for /f "delims=" %%J in ('where java 2^>nul') do (
    if not defined JAVA_CMD (
        if exist "%%~fJ" (
            set "JAVA_CMD=%%~fJ"
            REM tenta inferir JAVA_HOME a partir do java.exe
            for %%D in ("%%~dpJ.") do set "JAVA_HOME=%%~fD"
        )
    )
)
if defined JAVA_CMD goto :java_found

REM --- 4) Fallback: assume `java` no PATH ---
set "JAVA_CMD=java"
set "JAVA_HOME="

:java_found
REM --- Limpa variavel temporaria ---
set "_JDK_GLOBS="
exit /b 0

REM ============================================================================
REM  Sub-rotina: normaliza JAVA_HOME
REM  Remove aspas, barras finais e sufixos \bin\java.exe
REM ============================================================================
:normalize_java_home
    set "_JH=%JAVA_HOME:"=%"
    REM remove barra invertida final
    if "%_JH:~-1%"=="\" set "_JH=%_JH:~0,-1%"
    REM se termina em "\bin\java.exe" ou "\bin\java", volta para a pasta do JDK
    if /i "%_JH:~-13%"=="\bin\java.exe" set "_JH=%_JH:~0,-13%"
    if /i "%_JH:~-9%"=="\bin\java"    set "_JH=%_JH:~0,-9%"
    if /i "%_JH:~-4%"=="\bin"         set "_JH=%_JH:~0,-4%"
    set "JAVA_HOME=%_JH%"
    set "_JH="
    exit /b 0