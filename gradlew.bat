@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
@rem Memoria do daemon do Gradle. 64m era pouco e quebrava a Opcao 3 do menu
@rem (que dispara :app-dist:jar em um projeto multi-modulo Java+Kotlin).
@rem 2g cobre o build incremental com folga; ajuste GRADLE_OPTS no ambiente se precisar mais.
set DEFAULT_JVM_OPTS="-Xmx2g" "-Xms256m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem ---------------------------------------------------------------------------
@rem Brproject: atalhos (na raiz do projeto). Uso:
@rem   gradlew.bat br-menu                       - abre menu interativo (brproject-menu.bat)
@rem   gradlew.bat 1  /  br-compile              - build INCREMENTAL (compila o que mudou + gera libs/server.jar)
@rem   gradlew.bat 2  /  br-compile-clean        - CLEAN + build completo (apaga saida, recompila tudo + gera libs/server.jar)
@rem   gradlew.bat 3  /  br-start                - apenas gera libs/server.jar (se faltar) + inicia Login + Game
@rem   gradlew.bat br-ant-dist-test              - PrepararTeste (com GUI 1a vez) + inicia Login + Game
@rem ---------------------------------------------------------------------------
if /i "%~1"=="br-menu" (
    call "%APP_HOME%\brproject-menu.bat"
    goto end
)
if /i "%~1"=="br-compile" (
    "%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain brCompileIncremental
    goto end
)
if /i "%~1"=="1" (
    "%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain brCompileIncremental
    goto end
)
if /i "%~1"=="br-compile-clean" (
    REM ATENCAO: usamos clean brCompileClean com duas tasks-raiz. Isso forca o Gradle a ordenar
    REM :clean ANTES de brCompileClean e seu grafo de dependencias :app-dist:jar etc.,
    REM eliminando o bug em que o clean rodava em paralelo/depois do build e apagava libs/server.jar.
    "%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain clean brCompileClean
    goto end
)
if /i "%~1"=="2" (
    "%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain clean brCompileClean
    goto end
)
if /i "%~1"=="br-start" (
    echo.
    echo [BrProject] Modo 3: gerar libs/server.jar e iniciar Login + Game.
    "%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain :app-dist:jar
    if errorlevel 1 goto fail

    @rem Se for primeira execucao, prepara ambiente UMA vez antes de abrir Login/Game.
    @rem Isso evita que StartLogin e StartGame abram dois paineis simultaneos.
    if not exist "%APP_HOME%\flag\PrepararTeste.done" (
        if exist "%APP_HOME%\game\config\server.properties.example" goto :prep_first_run
        if exist "%APP_HOME%\game\configs\server.properties.example" goto :prep_first_run
    )

    REM --- Inicia Login e Game em janelas TOTALMENTE OCULTAS - sem flash de CMD.
    REM O helper tools\run-hidden.ps1 usa Process.StartInfo com WindowStyle=Hidden
    REM e CreateNoWindow=true, devolvendo o controle ao gradlew.bat imediatamente.
    REM Os logs do servidor continuam em logs\login-server.log e logs\game-server.log.
    echo.
    echo Iniciando StartLogin_SemDashboard.bat - janela oculta...
    if exist "%APP_HOME%\StartLogin_SemDashboard.bat" (
        start "" /B powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%APP_HOME%\tools\run-hidden.ps1" "Login Server" "%APP_HOME%\StartLogin_SemDashboard.bat"
    ) else (
        echo Aviso: StartLogin_SemDashboard.bat nao encontrado em %APP_HOME%!
    )

    echo Iniciando StartGame_SemDashboard.bat - janela oculta...
    if exist "%APP_HOME%\StartGame_SemDashboard.bat" (
        start "" /B powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%APP_HOME%\tools\run-hidden.ps1" "Game Server" "%APP_HOME%\StartGame_SemDashboard.bat"
    ) else (
        echo Aviso: StartGame_SemDashboard.bat nao encontrado em %APP_HOME%!
    )
    goto end

    :prep_first_run
    echo.
    echo [BrProject] Primeira execucao detectada. Abrindo Preparar Ambiente...
    "%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain PrepararTeste
    if errorlevel 1 goto fail
    echo.
    echo Iniciando StartLogin_SemDashboard.bat - janela oculta...
    if exist "%APP_HOME%\StartLogin_SemDashboard.bat" (
        start "" /B powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%APP_HOME%\tools\run-hidden.ps1" "Login Server" "%APP_HOME%\StartLogin_SemDashboard.bat"
    ) else (
        echo Aviso: StartLogin_SemDashboard.bat nao encontrado em %APP_HOME%!
    )
    echo Iniciando StartGame_SemDashboard.bat - janela oculta...
    if exist "%APP_HOME%\StartGame_SemDashboard.bat" (
        start "" /B powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%APP_HOME%\tools\run-hidden.ps1" "Game Server" "%APP_HOME%\StartGame_SemDashboard.bat"
    ) else (
        echo Aviso: StartGame_SemDashboard.bat nao encontrado em %APP_HOME%!
    )
    goto end
)
if /i "%~1"=="3" (
    call "%~f0" br-start
    goto end
)
if /i "%~1"=="br-ant-dist-test" (
    "%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain PrepararTeste

    @rem Verifica se a task do Gradle falhou. Se falhar, nao tenta abrir os servidores.
    if errorlevel 1 (
        echo.
        echo [ERRO] Falha ao executar a task PrepararTeste no Gradle. Servidores nao serao iniciados.
        goto fail
    )

    echo.
    echo Preparar Teste.

    @rem # STREAMING_CHUNK:Starting Login Server
    echo Iniciando StartLogin_SemDashboard.bat - janela oculta...
    if exist "%APP_HOME%\StartLogin_SemDashboard.bat" (
        start "" /B powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%APP_HOME%\tools\run-hidden.ps1" "Login Server" "%APP_HOME%\StartLogin_SemDashboard.bat"
    ) else (
        echo Aviso: Arquivo StartLogin_SemDashboard.bat nao encontrado!
    )

    @rem # STREAMING_CHUNK:Starting Game Server
    echo Iniciando StartGame_SemDashboard.bat - janela oculta...
    if exist "%APP_HOME%\StartGame_SemDashboard.bat" (
        start "" /B powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%APP_HOME%\tools\run-hidden.ps1" "Game Server" "%APP_HOME%\StartGame_SemDashboard.bat"
    ) else (
        echo Aviso: Arquivo StartGame_SemDashboard.bat nao encontrado!
    )
    goto end
)

@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the script return code instead of
rem the cmd.exe /c return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega