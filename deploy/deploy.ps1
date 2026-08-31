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

.PARAMETER Target
    api | ui | all   (default: all)

.PARAMETER SkipBuild
    Ship whatever is already in target/ and dist/ without rebuilding.

.EXAMPLE
    .\deploy.ps1
    .\deploy.ps1 -Target ui
    .\deploy.ps1 -Target api -SkipBuild
#>
[CmdletBinding()]
param(
    [ValidateSet('api', 'ui', 'all')]
    [string]$Target = 'all',
    [switch]$SkipBuild,
    [string]$ServerHost = '89.167.106.195',
    [string]$SshKey = "$env:USERPROFILE\.ssh\hetzner_key",
    [string]$RemoteDir = '/opt/saicomex'
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$ApiDir   = Join-Path $RepoRoot 'saicomex-api'
$UiDir    = Join-Path $RepoRoot 'saicomex-ui'

function Say([string]$Message) { Write-Host "`n==> $Message" -ForegroundColor Yellow }
function Ssh([string]$Command)  { & ssh -i $SshKey "root@$ServerHost" $Command; if ($LASTEXITCODE -ne 0) { throw "Remote command failed: $Command" } }
function Copy-Up([string]$Local, [string]$Remote) { & scp -i $SshKey -r $Local "root@${ServerHost}:$Remote"; if ($LASTEXITCODE -ne 0) { throw "scp failed: $Local" } }

if (-not (Test-Path $SshKey)) { throw "SSH key not found at $SshKey" }

Say "Checking the server is reachable"
Ssh "echo connected; docker --version"

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
    Ssh "mkdir -p $RemoteDir/build/api"
    Copy-Up (Join-Path $ApiDir 'pom.xml')    "$RemoteDir/build/api/"
    Copy-Up (Join-Path $ApiDir 'Dockerfile') "$RemoteDir/build/api/"
    Copy-Up (Join-Path $ApiDir 'src')        "$RemoteDir/build/api/"
    Ssh "cd $RemoteDir/build/api && docker build -t saicomex-api:latest ."
}

# ----------------------------------------------------------------- UI
if ($Target -in 'ui', 'all') {
    if (-not $SkipBuild) {
        Say "Building the Angular bundle"
        Push-Location $UiDir
        try {
            & npm ci
            & npm run build -- --configuration production
            if ($LASTEXITCODE -ne 0) { throw 'Angular build failed' }
        } finally { Pop-Location }
    }

    Say "Shipping the UI and building the image on the server"
    Ssh "rm -rf $RemoteDir/build/ui && mkdir -p $RemoteDir/build/ui/dist"
    Copy-Up (Join-Path $UiDir 'dist\saicomex-ui\browser') "$RemoteDir/build/ui/dist/"
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
    Ssh "cd $RemoteDir/build/ui && docker build -t saicomex-ui:latest ."
}

# ------------------------------------------------------------ compose
Say "Updating the compose file"
Copy-Up (Join-Path $PSScriptRoot 'docker-compose.yml') "$RemoteDir/"

Say "Restarting the stack"
# Flyway runs on API start and applies any pending migration. There is no
# manual gate — see docs/DEPLOYMENT.md before deploying a jar that carries one.
Ssh "cd $RemoteDir && docker compose up -d"

Say "Waiting for the API to report healthy"
$healthy = $false
foreach ($attempt in 1..30) {
    Start-Sleep -Seconds 5
    $status = & ssh -i $SshKey "root@$ServerHost" "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8090/actuator/health"
    if ($status -eq '200') { $healthy = $true; break }
    Write-Host "  attempt $attempt : $status"
}

if (-not $healthy) {
    Write-Host "`nAPI did not become healthy. Last 60 log lines:" -ForegroundColor Red
    Ssh "docker logs comex-api --tail 60"
    throw 'Deploy finished but the API is not healthy'
}

Say "Deployed"
Ssh "docker ps --filter name=comex- --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'"
Write-Host "`nhttps://comex.saifleet.co.za`n" -ForegroundColor Green
