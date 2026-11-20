param(
    [Parameter(Mandatory=$true)][ValidateSet('E0','E1','E2','E2_OFF')][string]$Case,
    [Parameter(Mandatory=$true)][string]$RunDirectory,
    [Parameter(Mandatory=$true)][string]$GradleExecutable,
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$SdkHome = $env:ANDROID_HOME
)
$ErrorActionPreference = 'Continue'
$run = (Resolve-Path -LiteralPath $RunDirectory).Path
$metadata = Get-Content -LiteralPath (Join-Path $run 'snapshot.json') -Raw | ConvertFrom-Json
$configuration = $metadata.cases.$Case
if (-not (Test-Path -LiteralPath $GradleExecutable -PathType Leaf)) { throw 'An existing Gradle 8.13 executable is required.' }
if (-not (Test-Path -LiteralPath (Join-Path $JdkHome 'bin/javac.exe'))) { throw 'An existing JDK 17 is required.' }
$project = Join-Path $run $Case
# Refuse a changed snapshot instead of claiming comparable build conditions.
foreach ($file in $configuration.files.PSObject.Properties) {
    $hash = (Get-FileHash -LiteralPath (Join-Path $project $file.Name) -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne $file.Value) { throw "Snapshot changed: $($file.Name)" }
}
$output = Join-Path $run 'artifacts'
New-Item -ItemType Directory -Path $output -Force | Out-Null
$artifact = Join-Path $output "$Case.aab"
if (Test-Path -LiteralPath $artifact) { throw 'Artifact exists. Create a new run; never silently overwrite a condition.' }
$prior = @{}
foreach ($name in @('JAVA_HOME','ANDROID_HOME','Path')) { $prior[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
try {
    $env:JAVA_HOME = $JdkHome; $env:ANDROID_HOME = $SdkHome; $env:Path = "$JdkHome\bin;$env:Path"
    $optimize = ([string]$configuration.sizeOptimization).ToLowerInvariant()
    $arguments = @('-p',$project,':app:bundleRelease','--no-daemon','--console=plain',"-PsizeOptimization=$optimize",'-Pandroid.builder.sdkDownload=false','-Dorg.gradle.java.installations.auto-download=false')
    Write-Output "$GradleExecutable $($arguments -join ' ')"
    & $GradleExecutable @arguments 2>&1 | Tee-Object -FilePath (Join-Path $output "$Case.log")
    $code = $LASTEXITCODE
    $result = [ordered]@{case=$Case;exit_code=$code;base_commit=$metadata.base_commit;dirty=$metadata.dirty;source_sha256=$metadata.source_sha256;sizeOptimization=$configuration.sizeOptimization;delivery=$configuration.delivery;arguments=$arguments}
    if ($code -eq 0) {
        Copy-Item -LiteralPath (Join-Path $project 'app/build/outputs/bundle/release/app-release.aab') -Destination $artifact -ErrorAction Stop
        $result['artifact_sha256'] = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    $result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $output "$Case-result.json") -Encoding UTF8
} finally {
    foreach ($name in $prior.Keys) { [Environment]::SetEnvironmentVariable($name,$prior[$name],'Process') }
}
exit $code
