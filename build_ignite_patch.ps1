$projectDir = "C:\Users\pigku\.gemini\antigravity-ide\scratch\HaoHanMetallurgy"
$patchDir = "$projectDir\ignite-patch"
$patchJar = "$patchDir\target\HaoHanMetallurgy-IgnitePatch-1.0-SNAPSHOT.jar"
$serverDir = "F:\TestServer"
$serverModsDir = "$serverDir\mods"
$igniteJar = "$serverDir\ignite.jar"
$igniteUrl = "https://github.com/vectrix-space/ignite/releases/download/v1.2.1/ignite.jar"
$mvnPath = "C:\Users\pigku\.gemini\antigravity-ide\tools\apache-maven-3.9.9\bin\mvn.cmd"

Write-Host "Building Ignite patch..." -ForegroundColor Cyan
Set-Location -Path $patchDir
& $mvnPath clean package
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Ignite patch build failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

New-Item -ItemType Directory -Force $serverModsDir | Out-Null

if (!(Test-Path $igniteJar)) {
    Write-Host "Downloading Ignite..." -ForegroundColor Cyan
    curl.exe -L --retry 3 --retry-delay 2 -o $igniteJar $igniteUrl
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Failed to download Ignite." -ForegroundColor Red
        exit $LASTEXITCODE
    }
}

Copy-Item -Force $patchJar "$serverModsDir\HaoHanMetallurgy-IgnitePatch-1.0-SNAPSHOT.jar"
Write-Host "Ignite patch deployed to $serverModsDir" -ForegroundColor Green
