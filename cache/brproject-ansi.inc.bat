@echo off
REM ============================================================================
REM  Brproject - Habilita cores ANSI no console do Windows (Virtual Terminal)
REM
REM  Por que isto existe:
REM    O Team.java imprime o banner usando sequencias ANSI (\u001B[96m,
REM    \u001B[1m, etc.). Em terminais modernos (Windows Terminal, ConEmu,
REM    bash/WSL) isso vira cor normalmente. No cmd.exe "classico" do Windows,
REM    esses codigos sao impressos literalmente como "←[96m" - exatamente o
REM    que o usuario reportou como bug.
REM
REM  O que este helper faz:
REM    1. Seta HKCU\Console\VirtualTerminalLevel=1 no registro - afeta
REM       TODAS as novas janelas de console do usuario (sem precisar admin).
REM    2. Aplica ENABLE_VIRTUAL_TERMINAL_PROCESSING (0x4) no console ATUAL
REM       via PowerShell + P/Invoke em kernel32.dll. Resolve o problema
REM       imediatamente, sem precisar reabrir a janela.
REM
REM  Idempotente: rodar varias vezes nao causa efeito colateral.
REM  Silencioso: nao imprime nada quando ja esta habilitado.
REM ============================================================================

REM --- 1) Registro: futuras janelas ja abrem com VT ligado ---
reg add "HKCU\Console" /v VirtualTerminalLevel /t REG_DWORD /d 1 /f >nul 2>&1

REM --- 2) Console atual: ativa VT via PowerShell + SetConsoleMode ---
REM      STD_OUTPUT_HANDLE = -11;  ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x4
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$src = 'using System; using System.Runtime.InteropServices; public static class W { [DllImport(\"kernel32\")] public static extern IntPtr GetStdHandle(int n); [DllImport(\"kernel32\")] public static extern bool GetConsoleMode(IntPtr h, out int m); [DllImport(\"kernel32\")] public static extern bool SetConsoleMode(IntPtr h, int m); }'; " ^
    "Add-Type -TypeDefinition $src -ErrorAction SilentlyContinue; " ^
    "$h = [W]::GetStdHandle(-11); " ^
    "$m = 0; " ^
    "if ([W]::GetConsoleMode($h, [ref]$m)) { [W]::SetConsoleMode($h, $m -bor 4) | Out-Null }" >nul 2>&1

exit /b 0
