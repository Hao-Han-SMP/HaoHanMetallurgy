# Template script to build, deploy plugin and start test server
# Copy this file to build_and_start.ps1 and configure paths for your local environment

$projectDir = "C:\path\to\your\HaoHanMetallurgy"
$jarSource = "$projectDir\target\HaoHanMetallurgy-1.0-SNAPSHOT.jar"
$serverPluginsDir = "C:\path\to\your\TestServer\plugins"
$jarDest = "$serverPluginsDir\HaoHanMetallurgy-1.0-SNAPSHOT.jar"
$serverDir = "C:\path\to\your\TestServer"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "1. Building plugin with Maven..." -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# Change directory to project root
Set-Location -Path $projectDir

# Call Maven to package the jar
# Configure path to your local mvn.cmd if it is not in your system PATH
$mvnPath = "mvn"
& $mvnPath clean package -DskipTests

if ($LASTEXITCODE -ne 0) {
    Write-Host "`n[ERROR] Maven build failed! Please check your code compile errors." -ForegroundColor Red
    Read-Host "Press Enter to exit..."
    exit
}

Write-Host "`n==========================================" -ForegroundColor Green
Write-Host "2. Build successful! Copying JAR to TestServer..." -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green

# Copy JAR to server plugins folder
Copy-Item -Path $jarSource -Destination $jarDest -Force

Write-Host "`n==========================================" -ForegroundColor Cyan
Write-Host "3. Starting TestServer in a new window..." -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# Run start.bat in a new window
Start-Process -FilePath "cmd.exe" -ArgumentList "/c start.bat" -WorkingDirectory $serverDir

Write-Host "`n[SUCCESS] Deployment complete and server started!" -ForegroundColor Green
