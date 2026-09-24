[CmdletBinding()]
param(
	[int] $TimeoutSeconds = 300
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Gradle = Join-Path $Root "gradlew.bat"
$GradleHome = Join-Path $Root ".gradle-home"
$LogDirectory = Join-Path $Root "logs"

if (-not (Test-Path -LiteralPath $Gradle)) {
	throw "Gradle wrapper nao encontrado em $Gradle"
}

New-Item -ItemType Directory -Path $LogDirectory -Force | Out-Null
$env:GRADLE_USER_HOME = $GradleHome

function Test-PortOpen {
	param([int] $Port)
	try {
		return (Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -InformationLevel Quiet -WarningAction SilentlyContinue)
	}
	catch {
		return $false
	}
}

function Start-L2NewEraService {
	param(
		[string] $Name,
		[string] $GradleTask,
		[int] $ReadyPort
	)

	if (Test-PortOpen $ReadyPort) {
		Write-Host "[$Name] ja esta ativo na porta $ReadyPort."
		return
	}

	$logFile = Join-Path $LogDirectory ("{0}-launcher.log" -f $Name.ToLowerInvariant())
	$command = "& '$Gradle' $GradleTask --no-daemon --no-parallel *>> '$logFile'"
	$process = Start-Process -FilePath "powershell.exe" `
		-ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $command) `
		-WorkingDirectory $Root -WindowStyle Hidden -PassThru

	Write-Host "[$Name] iniciando em segundo plano..."
	$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
	while ((Get-Date) -lt $deadline) {
		if (Test-PortOpen $ReadyPort) {
			Write-Host "[$Name] pronto na porta $ReadyPort."
			return
		}
		if ($process.HasExited) {
			throw "[$Name] encerrou antes de abrir a porta $ReadyPort. Consulte $logFile"
		}
		Start-Sleep -Seconds 2
	}

	throw "[$Name] nao abriu a porta $ReadyPort em $TimeoutSeconds segundos. Consulte $logFile"
}

Write-Host "=== Lineage2 NewEra - inicializacao local ==="
Write-Host "Os servicos serao iniciados em segundo plano; logs: $LogDirectory"

# O GameServer precisa estar pronto antes de o LoginServer registrar o mundo.
Start-L2NewEraService "GameServer" ":game-server-core:run" 7778
Start-L2NewEraService "LoginServer" ":login-server:run" 2107
Start-L2NewEraService "Proxy" ":proxy:run" 7777

Write-Host "=== Lineage2 NewEra pronto ==="
Write-Host "Login: 2106 | Game: 7777 | Logs: $LogDirectory"
