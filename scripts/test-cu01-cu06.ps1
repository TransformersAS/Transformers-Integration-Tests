param(
    [ValidateRange(1, 6)]
    [int[]] $Case = @(1, 2, 3, 4, 5, 6),
    [string] $BackendPath = (Join-Path $PSScriptRoot '../../Transformers-AS/backend/demo'),
    [string] $JavaHome
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$backend = (Resolve-Path -LiteralPath $BackendPath).Path
$app = Join-Path $repo 'target/cu01-cu06-validation/app'
$external = Join-Path $app 'src/test/java/com/transformersas/marketplace/external'
if ($JavaHome) {
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
} else {
    # Use the java executable on PATH instead of an outdated inherited JAVA_HOME.
    $env:JAVA_HOME = Split-Path (Split-Path (Get-Command java -CommandType Application).Source -Parent) -Parent
}
if (-not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
    throw 'Provide -JavaHome with the path to a JDK 21 installation.'
}
New-Item -ItemType Directory -Force $app | Out-Null
Copy-Item -LiteralPath (Join-Path $backend 'src'), (Join-Path $backend '.mvn') -Destination $app -Recurse -Force
Copy-Item -LiteralPath (Join-Path $backend 'pom.xml'), (Join-Path $backend 'mvnw.cmd') -Destination $app -Force
New-Item -ItemType Directory -Force $external | Out-Null
Get-ChildItem -LiteralPath (Join-Path $repo 'backend-tests/src/test/java/com/transformersas/marketplace/external') -Filter '*.java' |
    Copy-Item -Destination $external -Force
$patterns = ($Case | Sort-Object -Unique | ForEach-Object { 'Cu{0:00}*IntegrationTests' -f $_ }) -join ','
$testExit = 1
Push-Location $app
try {
    & .\mvnw.cmd clean test "-Dtest=$patterns"
    $testExit = $LASTEXITCODE
    # Generate diagnostic coverage even when an acceptance contract fails.
    & .\mvnw.cmd jacoco:report
    if ($LASTEXITCODE -ne 0 -and $testExit -eq 0) { $testExit = $LASTEXITCODE }
} finally {
    Pop-Location
}
exit $testExit
