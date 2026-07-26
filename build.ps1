# PowerShell script to download Maven, build the plugin, and output the JAR.
$ErrorActionPreference = "Stop"

$ProjectDir = Get-Location
$MavenDir = Join-Path $ProjectDir "apache-maven-3.9.6"
$ZipPath = Join-Path $ProjectDir "maven.zip"

# 1. Download and Extract Maven if not present
if (-not (Test-Path $MavenDir)) {
    Write-Host "Maven not found. Downloading Maven 3.9.6..." -ForegroundColor Cyan
    Invoke-WebRequest -Uri "https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip" -OutFile $ZipPath
    
    Write-Host "Extracting Maven..." -ForegroundColor Cyan
    Expand-Archive -Path $ZipPath -DestinationPath $ProjectDir
    
    Write-Host "Cleaning up zip file..." -ForegroundColor Cyan
    Remove-Item $ZipPath
    Write-Host "Maven setup complete." -ForegroundColor Green
} else {
    Write-Host "Maven found in project directory." -ForegroundColor Green
}

$MvnPath = Join-Path $MavenDir "bin\mvn.cmd"

# 2. Run Maven Package
Write-Host "Compiling and packaging RZXPets..." -ForegroundColor Cyan
& $MvnPath clean package

Write-Host "Build finished!" -ForegroundColor Green
