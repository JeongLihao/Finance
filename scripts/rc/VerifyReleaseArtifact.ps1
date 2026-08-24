param([string]$JarPath = "build/libs/finance-0.4.1.jar")
$ErrorActionPreference = "Stop"
$resolved = (Resolve-Path -LiteralPath $JarPath).Path
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($resolved)
try {
    $names = @($archive.Entries | ForEach-Object FullName)
    $required = @("META-INF/mods.toml", "assets/finance/lang/en_us.json", "assets/finance/lang/zh_cn.json", "data/finance/recipes/survey_board.json")
    foreach ($entry in $required) { if ($entry -notin $names) { throw "Missing required JAR entry: $entry" } }
    $forbidden = @($names | Where-Object { $_ -match '(^|/)(logs?|run|build|\.gradle|gameteststructures)/|\.class\.unique$|\.key$|\.pem$' })
    if ($forbidden.Count -gt 0) { throw "Forbidden release entries: $($forbidden -join ', ')" }
    foreach ($entry in $archive.Entries | Where-Object { $_.Length -le 2MB -and $_.FullName -match '\.(json|toml|mcmeta|md|txt)$' }) {
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if ($text -match 'C:\\Users\\|D:\\MCMOD\\|OPENAI_API_KEY|BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY') { throw "Personal path or secret marker in $($entry.FullName)" }
    }
} finally { $archive.Dispose() }
$item = Get-Item -LiteralPath $resolved
$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolved).Hash
[pscustomobject]@{Path=$resolved;Bytes=$item.Length;Sha256=$hash;Entries=$names.Count;Status="PASS"}
