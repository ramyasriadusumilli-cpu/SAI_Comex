<#
.SYNOPSIS
    Build and deploy the SAIComex platform to the Hetzner VPS.

.DESCRIPTION
    Builds the API jar and the Angular bundle locally, ships them to the server,
    builds the container images there and restarts the stack.

    Building the images ON the server rather than pushing to a registry keeps
    this dependency-free — there is no registry to authenticate against and no
    image to garbage-collect. The cost is a slower deploy; on a two-container
    stack that is the right trade.

.PARAMETER Environment
    prod | uat   (default: prod). Selects which stack this deploy targets:
    prod -> /opt/saicomex,     docker-compose.yml,     health 8090, https://saicomex.saigroup.co.za
    uat  -> /opt/saicomex-uat, docker-compose.uat.yml, health 8092, https://uat.saicomex.saigroup.co.za
    Both stacks run the SAME image tags (saicomex-api:latest / saicomex-ui:latest),
    so a rebuild reaches whichever stack restarts next. The safe promotion order is
    therefore: deploy to UAT, verify, then deploy to prod WITH -SkipBuild so prod
    ships the exact bits UAT already ran.

.PARAMETER Target
    api | ui | all   (default: all)

.PARAMETER SkipBuild
    Ship whatever is already in target/ and dist/ without rebuilding.

.EXAMPLE
    .\deploy.ps1                              # build + deploy prod
    .\deploy.ps1 -Environment uat             # build + deploy UAT
    .\deploy.ps1 -Environment prod -SkipBuild # promote the already-built image to prod
    .\deploy.ps1 -Target ui
#>
[CmdletBinding()]
param(
    [ValidateSet('prod', 'uat')]
    [string]$Environment = 'prod',
    [ValidateSet('api', 'ui', 'all')]
    [string]$Target = 'all',
    [switch]$SkipBuild,
    [string]$ServerHost = '89.167.106.195',
    [string]$SshKey = "$env:USERPROFILE\.ssh\hetzner_key",
    [string]$RemoteDir
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$ApiDir   = Join-Path $RepoRoot 'saicomex-api'
$UiDir    = Join-Path $RepoRoot 'saicomex-ui'

# --- per-environment settings ------------------------------------------------
if ($Environment -eq 'uat') {
    $ComposeFile = 'docker-compose.uat.yml'
    $HealthPort  = 8092
    $PublicUrl   = 'https://uat.saicomex.saigroup.co.za'
    if (-not $RemoteDir) { $RemoteDir = '/opt/saicomex-uat' }
} else {
    $ComposeFile = 'docker-compose.yml'
    $HealthPort  = 8090
    $PublicUrl   = 'https://saicomex.saigroup.co.za'
    if (-not $RemoteDir) { $RemoteDir = '/opt/saicomex' }
}
Write-Host "`n### Deploying [$Environment] -> $RemoteDir (health :$HealthPort)" -ForegroundColor Cyan

# NB: the remote-exec function must NOT be named `Ssh`. PowerShell command
# names are case-insensitive, so a function named `Ssh` shadows the `ssh`
# executable — `& ssh ...` inside it would re-enter the function forever
# (CallDepthOverflow). Hence `Invoke-Remote`, and `.exe` on the externals.
function Say([string]$Message) { Write-Host "`n==> $Message" -ForegroundColor Yellow }
function Invoke-Remote([string]$Command) { & ssh.exe -i $SshKey "root@$ServerHost" $Command; if ($LASTEXITCODE -ne 0) { throw "Remote command failed: $Command" } }
function Copy-Up([string]$Local, [string]$Remote) { & scp.exe -i $SshKey -r $Local "root@${ServerHost}:$Remote"; if ($LASTEXITCODE -ne 0) { throw "scp failed: $Local" } }

if (-not (Test-Path $SshKey)) { throw "SSH key not found at $SshKey" }

Say "Checking the server is reachable"
Invoke-Remote "echo connected; docker --version"

# ---------------------------------------------------------------- API
if ($Target -in 'api', 'all') {
    if (-not $SkipBuild) {
        Say "Building the API jar"
        Push-Location $ApiDir
        try {
            & mvn -B clean package -DskipTests
            if ($LASTEXITCODE -ne 0) { throw 'Maven build failed' }
        } finally { Pop-Location }
    }

    Say "Shipping the API source and building the image on the server"
    Invoke-Remote "mkdir -p $RemoteDir/build/api"
    Copy-Up (Join-Path $ApiDir 'pom.xml')    "$RemoteDir/build/api/"
    Copy-Up (Join-Path $ApiDir 'Dockerfile') "$RemoteDir/build/api/"
    Copy-Up (Join-Path $ApiDir 'src')        "$RemoteDir/build/api/"
    Invoke-Remote "cd $RemoteDir/build/api && docker build -t saicomex-api:latest ."
}

# ----------------------------------------------------------------- UI
if ($Target -in 'ui', 'all') {
    # Where `ng build` lands (no explicit outputPath in angular.json -> dist/<project>/browser).
    $BrowserDir = Join-Path $UiDir 'dist\saicomex-ui\browser'
    if (-not $SkipBuild) {
        Say "Building the Angular bundle"
        Push-Location $UiDir
        try {
            & npm ci
            if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' }
            & npm run build -- --configuration production
            if ($LASTEXITCODE -ne 0) { throw 'Angular build failed' }
        } finally { Pop-Location }
    }

    # Guard: npm.cmd can report exit 0 even when nothing was emitted, which
    # would otherwise ship an empty directory. Refuse to continue unless the
    # bundle actually exists.
    if (-not (Test-Path (Join-Path $BrowserDir 'index.html'))) {
        throw "UI bundle missing at $BrowserDir - the Angular build did not emit. Run 'npm run build -- --configuration production' in saicomex-ui and check the output, then re-run with -SkipBuild."
    }

    Say "Shipping the UI and building the image on the server"
    Invoke-Remote "rm -rf $RemoteDir/build/ui && mkdir -p $RemoteDir/build/ui/dist"
    Copy-Up $BrowserDir "$RemoteDir/build/ui/dist/"
    Copy-Up (Join-Path $UiDir 'nginx.conf')               "$RemoteDir/build/ui/"

    # The UI image is built from the finished bundle rather than from source:
    # no need to install node on the server for something already compiled.
    $uiDockerfile = @'
FROM nginx:alpine
COPY dist/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
'@
    $tmp = New-TemporaryFile
    Set-Content -Path $tmp -Value $uiDockerfile -Encoding ASCII
    Copy-Up $tmp "$RemoteDir/build/ui/Dockerfile"
    Remove-Item $tmp
    Invoke-Remote "cd $RemoteDir/build/ui && docker build -t saicomex-ui:latest ."
}

# ------------------------------------------------------------ compose
Say "Updating the compose file ($ComposeFile -> $RemoteDir/docker-compose.yml)"
# Each environment's own compose is copied in as the canonical docker-compose.yml
# in its own directory, so `docker compose` in that dir always does the right thing.
Copy-Up (Join-Path $PSScriptRoot $ComposeFile) "$RemoteDir/docker-compose.yml"

Say "Restarting the stack"
# This host has Compose v1 (the standalone `docker-compose` binary), NOT the
# `docker compose` v2 plugin — same as the fleet stacks on this box. Using the
# v2 spelling fails with "docker: unknown command: docker compose".
# Flyway runs on API start and applies any pending migration. There is no
# manual gate — see docs/DEPLOYMENT.md before deploying a jar that carries one.
Invoke-Remote "cd $RemoteDir && docker-compose up -d"

Say "Waiting for the API to report healthy"
$healthy = $false
foreach ($attempt in 1..30) {
    Start-Sleep -Seconds 5
    $status = & ssh.exe -i $SshKey "root@$ServerHost" "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:$HealthPort/actuator/health"
    if ($status -eq '200') { $healthy = $true; break }
    Write-Host "  attempt $attempt : $status"
}

$apiContainer = if ($Environment -eq 'uat') { 'comex-api-uat' } else { 'comex-api' }
if (-not $healthy) {
    Write-Host "`nAPI did not become healthy. Last 60 log lines:" -ForegroundColor Red
    Invoke-Remote "docker logs $apiContainer --tail 60"
    throw 'Deploy finished but the API is not healthy'
}

Say "Deployed [$Environment]"
Invoke-Remote "docker ps --filter name=comex- --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'"
Write-Host "`n$PublicUrl`n" -ForegroundColor Green
