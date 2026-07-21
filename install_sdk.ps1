$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:PATH = "C:\Android\cmdline\cmdline-tools\bin;${env:PATH}"
$sdktRoot = 'C:\Android\sdk'
$tool = 'C:\Android\cmdline\cmdline-tools\bin\sdkmanager.bat'
Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "SDK Manager=$tool"
& $tool --sdk_root=$sdktRoot 'platform-tools' 'platforms;android-34' 'build-tools;34.0.0' --verbose
