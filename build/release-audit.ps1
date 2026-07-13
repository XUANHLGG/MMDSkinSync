$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.IO.Compression.FileSystem

$jars = @(
    @{
        Name = '1.21.1 Fabric'
        Path = 'MMDSkinSync/dist/1.21.1/mmdsync-fabric-1.21.1-1.1.0.jar'
        Meta = 'fabric.mod.json'
    },
    @{
        Name = '1.21.1 NeoForge'
        Path = 'MMDSkinSync/dist/1.21.1/mmdsync-neoforge-1.21.1-1.1.0.jar'
        Meta = 'META-INF/neoforge.mods.toml'
    },
    @{
        Name = '1.21.4 Fabric'
        Path = 'MMDSkinSync/dist/1.21.4/mmdsync-fabric-1.21.4-1.1.0.jar'
        Meta = 'fabric.mod.json'
    },
    @{
        Name = 'Bukkit'
        Path = 'MmdSkin-Bukkit/build/libs/MmdSkin-Bukkit-1.2.0.jar'
        Meta = 'plugin.yml'
    }
)

foreach ($jar in $jars) {
    $fullPath = (Resolve-Path -LiteralPath $jar.Path).Path
    $zip = [System.IO.Compression.ZipFile]::OpenRead($fullPath)
    try {
        $ownMajors = @{}
        $shadowMajors = @{}
        $ownClassCount = 0
        $unrelocatedCompress = 0
        $staleMmdSkinClasses = 0

        foreach ($entry in $zip.Entries) {
            if ($entry.FullName.StartsWith('org/apache/commons/compress/')) {
                $unrelocatedCompress++
            }
            if ($entry.FullName.StartsWith('com/shiroha/mmdskin/') -and $entry.FullName.EndsWith('.class')) {
                $staleMmdSkinClasses++
            }
            if (-not $entry.FullName.EndsWith('.class')) {
                continue
            }

            $isOwn = ($entry.FullName.StartsWith('com/opdent/') -or $entry.FullName.StartsWith('com/tendoarisu/')) -and -not $entry.FullName.Contains('/shadow/')
            $isShadow = $entry.FullName.Contains('/shadow/')
            if (-not $isOwn -and -not $isShadow) {
                continue
            }

            $stream = $entry.Open()
            try {
                $header = New-Object byte[] 8
                $read = $stream.Read($header, 0, 8)
                if ($read -ne 8 -or $header[0] -ne 202 -or $header[1] -ne 254 -or $header[2] -ne 186 -or $header[3] -ne 190) {
                    throw "Invalid class header: $($entry.FullName)"
                }
                $major = ([int]$header[6] * 256) + [int]$header[7]
            }
            finally {
                $stream.Dispose()
            }

            if ($isOwn) {
                if (-not $ownMajors.ContainsKey($major)) {
                    $ownMajors[$major] = 0
                }
                $ownMajors[$major]++
                $ownClassCount++
            }
            else {
                if (-not $shadowMajors.ContainsKey($major)) {
                    $shadowMajors[$major] = 0
                }
                $shadowMajors[$major]++
            }
        }

        $metaEntry = $zip.GetEntry($jar.Meta)
        if ($null -eq $metaEntry) {
            throw "Missing metadata: $($jar.Meta)"
        }
        $reader = New-Object System.IO.StreamReader($metaEntry.Open(), [System.Text.Encoding]::UTF8, $true)
        try {
            $metadata = $reader.ReadToEnd()
        }
        finally {
            $reader.Dispose()
        }

        $ownMajorText = (($ownMajors.GetEnumerator() | Sort-Object Name | ForEach-Object { "$( $_.Name ):$( $_.Value )" }) -join ',')
        $shadowMajorText = (($shadowMajors.GetEnumerator() | Sort-Object Name | ForEach-Object { "$( $_.Name ):$( $_.Value )" }) -join ',')

        Write-Output "=== $($jar.Name) ==="
        Write-Output "SHA256=$((Get-FileHash -LiteralPath $fullPath -Algorithm SHA256).Hash.ToLowerInvariant())"
        Write-Output "SIZE=$((Get-Item -LiteralPath $fullPath).Length)"
        Write-Output "OWN_CLASS_MAJOR=$ownMajorText"
        Write-Output "SHADOW_CLASS_MAJOR=$shadowMajorText"
        Write-Output "OWN_CLASS_COUNT=$ownClassCount"
        Write-Output "UNRELOCATED_COMPRESS=$unrelocatedCompress"
        Write-Output "STALE_MMDSKIN_CLASSES=$staleMmdSkinClasses"

        if ($jar.Meta -eq 'fabric.mod.json') {
            $parsed = $metadata | ConvertFrom-Json
            Write-Output "META=id:$($parsed.id);version:$($parsed.version);minecraft:$($parsed.depends.minecraft)"
        }
        elseif ($jar.Meta -eq 'plugin.yml') {
            $name = [regex]::Match($metadata, '(?m)^name:\s*(.+)$').Groups[1].Value.Trim()
            $version = [regex]::Match($metadata, '(?m)^version:\s*(.+)$').Groups[1].Value.Trim()
            Write-Output "META=name:$name;version:$version"
        }
        else {
            $modsMatch = [regex]::Match($metadata, '(?ms)\[\[mods\]\].*?modId\s*=\s*"([^"]+)".*?version\s*=\s*"([^"]+)"')
            $minecraftMatch = [regex]::Match($metadata, '(?ms)\[\[dependencies\.[^\]]+\]\].*?modId\s*=\s*"minecraft".*?versionRange\s*=\s*"([^"]+)"')
            Write-Output "META=id:$($modsMatch.Groups[1].Value);version:$($modsMatch.Groups[2].Value);minecraft:$($minecraftMatch.Groups[1].Value)"
        }
    }
    finally {
        $zip.Dispose()
    }
}

Write-Output '=== JAVA/MINECRAFT PROCESSES ==='
$processes = Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^(java|javaw|minecraft|HMCL).*\.exe$' -or
    $_.CommandLine -match '(?i)minecraft|fabric-loader|neoforge|knot|HMCL'
}
if ($processes) {
    foreach ($process in $processes) {
        $isClient = $process.Name -match '^(javaw|minecraft).*\.exe$' -or $process.CommandLine -match '(?i)net\.fabricmc\.loader\.impl\.launch\.knot|minecraftforge|neoforge.*client|--gameDir'
        Write-Output "PROCESS=pid:$($process.ProcessId);name:$($process.Name);minecraftClient:$isClient;command:$($process.CommandLine)"
    }
}
else {
    Write-Output 'NONE'
}

Write-Output '=== TARGET PRESTATE ==='
$targets = @(
    '../HMCL/.minecraft/versions/1.21.1-Fabric/mods',
    '../HMCL/.minecraft/versions/1.21.4-Fabric/mods'
)
foreach ($target in $targets) {
    Write-Output "-- $target"
    Get-ChildItem -LiteralPath $target -File |
        Where-Object { $_.Name -match '(?i)mmdskin|mmdsync' } |
        Sort-Object Name |
        ForEach-Object {
            Write-Output "$($_.Name)|$($_.Length)|$((Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant())"
        }
}
