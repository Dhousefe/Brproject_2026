# ============================================================================
#  run-hidden.ps1 — BrProject helper
#
#  Inicia um processo (ex.: StartLogin_SemDashboard.bat) em uma janela
#  TOTALMENTE OCULTA (WindowStyle = Hidden) sem precisar de ferramentas
#  externas (cmdow, nircmd, etc.).
#
#  O processo fica detached: este script retorna imediatamente e o cmd
#  iniciado continua rodando em background. Os logs do .bat/console
#  permanecem acessiveis nos arquivos de log configurados pelo proprio
#  script .bat (ex.: logs/login-server.log).
#
#  Uso (a partir de um .bat):
#      powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden ^
#          -File "%~dp0tools\run-hidden.ps1" "Titulo da Janela" "C:\caminho\para\StartLogin_SemDashboard.bat"
#
#  Parametros:
#      $args[0] = titulo da janela oculta (apenas rotulagem interna)
#      $args[1] = comando / arquivo .bat a executar
#
#  Por que nao usar WScript.Shell.Run com WindowStyle = Hidden diretamente
#  de um .bat? Porque WScript.Shell nao vem exposto em PowerShell standard
#  por padrao; System.Diagnostics.Process com ProcessWindowStyle.Hidden e
#  CreateNoWindow = $true da o mesmo efeito de forma idiomática e portatil.
# ============================================================================

param(
    [Parameter(Mandatory=$true, Position=0)] [string] $Title,
    [Parameter(Mandatory=$true, Position=1)] [string] $Command,
    [Parameter(ValueFromRemainingArguments=$true)] [string[]] $CommandArgs
)

$ErrorActionPreference = 'Stop'

try {
    # ProcessStartInfo permite configurar o handle de log e o estilo da
    # janela antes mesmo do processo iniciar, evitando o flash de janela
    # que ocorre com Start-Process -WindowStyle Hidden em versoes antigas.
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = 'cmd.exe'

    # /c + call garante que .bat seja interpretado pelo cmd.
    # Windows PowerShell 5.x roda em .NET Framework, onde ProcessStartInfo.ArgumentList
    # pode estar indisponivel/nulo. Por isso montamos Arguments manualmente com aspas.
    function Quote-CmdArg([string] $value) {
        return '"' + ($value -replace '"', '""') + '"'
    }

    $parts = @('/c', 'call', (Quote-CmdArg $Command))
    foreach ($arg in $CommandArgs) {
        $parts += (Quote-CmdArg $arg)
    }
    $psi.Arguments = ($parts -join ' ')

    $psi.UseShellExecute        = $false   # necessario para definir CreateNoWindow
    $psi.CreateNoWindow         = $true    # NAO cria janela de console
    $psi.WindowStyle            = [System.Diagnostics.ProcessWindowStyle]::Hidden
    $psi.RedirectStandardOutput = $false   # logs ficam por conta do proprio .bat
    $psi.RedirectStandardError  = $false

    # CRUCIAL: redirecionar stdin para $null. Caso contrario o powershell pode manter
    # handles abertas do processo filho (mesmo com CreateNoWindow), atrasando o retorno.
    $psi.RedirectStandardInput  = $true

    $proc = [System.Diagnostics.Process]::Start($psi)
    if ($proc -and $proc.StartInfo.RedirectStandardInput) {
        $proc.StandardInput.Close()
    }
    # Nao esperamos o processo terminar (senao o gradle travaria):
    # o processo java do servidor fica rodando em background, detached do pai.

    # Apenas rotula o titulo interno do processo (util no Task Manager).
    if ($proc -and -not $proc.HasExited) {
        # Rotulo so e visivel no Task Manager via Process.MainWindowTitle.
        # Como nao existe janela, isso nao tera efeito visual — mas pode
        # ajudar no debug.
    }

    exit 0
} catch {
    Write-Error "[run-hidden.ps1] Falha ao iniciar '$Command': $_"
    exit 1
}
