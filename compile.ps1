$MvnPath = "C:\Users\Hi\.gemini\antigravity\scratch\zcompanions-project\apache-maven-3.9.6\bin\mvn.cmd"

Write-Host "Compiling and packaging RZXPets with Maven..."
& $MvnPath clean package

if ($LASTEXITCODE -eq 0) {
    $targetJar = "C:\Users\Hi\.gemini\antigravity\scratch\zcompanions-project\target\RZXPets.jar"
    $petShopConfigJar = "C:\Users\Hi\.gemini\antigravity\scratch\pet-shop-configs\RZXPets.jar"
    Copy-Item -Path $targetJar -Destination $petShopConfigJar -Force
    Write-Host "BUILD SUCCESS! Shaded artifact created: $targetJar and synced to $petShopConfigJar"
} else {
    Write-Error "Compilation failed with exit code $LASTEXITCODE"
}
