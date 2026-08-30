param()

$ErrorActionPreference = 'Stop'

$projectPath = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runPath = Join-Path $projectPath 'run'
$modsPath = Join-Path $runPath 'mods'
$mappingPath = Join-Path $projectPath 'build\createSrgToMcp\output.srg'
$runConfigPath = Join-Path $projectPath '.idea\runConfigurations\runClient.xml'
$classpathPath = Join-Path $projectPath 'build\classpath\runClient_minecraftClasspath.txt'
$staleRuntimeMods = @(
    'Ponder-Forge-1.20.1-1.0.92_mapped_official_1.20.1.jar',
    'flywheel-forge-1.20.1-1.0.6-281_mapped_official_1.20.1.jar'
)

foreach ($requiredPath in @($mappingPath, $runConfigPath, $classpathPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Missing generated userdev file: $requiredPath"
    }
}
foreach ($modName in $staleRuntimeMods) {
    $modPath = Join-Path $modsPath $modName
    if (Test-Path -LiteralPath $modPath -PathType Leaf) {
        throw "Duplicate userdev dependency in run/mods: $modPath. Remove it; Gradle already supplies the mapped runtime dependency."
    }
}

function Split-LaunchArguments([string] $text) {
    return [regex]::Matches($text, '(?:"[^"]*"|\S+)') | ForEach-Object {
        $_.Value.Trim('"')
    }
}

[xml] $runConfig = Get-Content -LiteralPath $runConfigPath
$configuration = $runConfig.component.configuration
$vmText = ($configuration.option | Where-Object name -eq 'VM_PARAMETERS').value
$programText = ($configuration.option | Where-Object name -eq 'PROGRAM_PARAMETERS').value
$vmArguments = @(Split-LaunchArguments $vmText)
$programArguments = @(Split-LaunchArguments $programText)

$legacyClasspath = (Get-Content -LiteralPath $classpathPath) -join ';'
$bootstrapJar = ($legacyClasspath -split ';' | Where-Object {
    [System.IO.Path]::GetFileName($_) -like 'bootstraplauncher-*.jar'
} | Select-Object -First 1)
if (-not $bootstrapJar -or -not (Test-Path -LiteralPath $bootstrapJar)) {
    throw 'BootstrapLauncher was not found in the generated client classpath.'
}

$configuredJava = 'D:\soft\bin\java.exe'
$javaPath = if (Test-Path -LiteralPath $configuredJava) {
    $configuredJava
} else {
    (Get-Command java -ErrorAction Stop).Source
}

$env:MOD_CLASSES = "finance%%$projectPath\build\resources\main;finance%%$projectPath\build\classes\java\main"
$env:MCP_MAPPINGS = 'official_1.20.1'
$mixinArguments = @(
    '-Dmixin.env.remapRefMap=true',
    "-Dmixin.env.refMapRemappingFile=$mappingPath"
)

Push-Location $runPath
try {
    & $javaPath @vmArguments @mixinArguments -cp $bootstrapJar `
        cpw.mods.bootstraplauncher.BootstrapLauncher @programArguments
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
