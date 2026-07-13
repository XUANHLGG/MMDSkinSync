$ErrorActionPreference = 'Stop'

$deployments = @(
    @{
        Instance = '1.21.1-Fabric'
        Source = 'MMDSkinSync/dist/1.21.1/mmdsync-fabric-1.21.1-1.1.0.jar'
        SourceHash = '0ad2b1971467ad29f83e097421965ca0ff95cf7c0c9cabe5dd8732e623a7b332'
        TargetName = 'mmdsync-fabric-1.21.1-1.1.0.jar'
        RequiredMmdSkinPattern = '^mmdskin-fabric-1\.0\.5-1\.21\.1(?:-|\.).*\.jar$'
    },
    @{
        Instance = '1.21.4-Fabric'
        Source = 'MMDSkinSync/dist/1.21.4/mmdsync-fabric-1.21.4-1.1.0.jar'
        SourceHash = '2646262912fd1953fbd10271bddb0a4e5371f1f64da5e975738a00b97189389c'
        TargetName = 'mmdsync-fabric-1.21.4-1.1.0.jar'
        RequiredMmdSkinPattern = '^mmdskin-fabric-1\.0\.5-1\.21\.4(?:-|\.).*\.jar$'
    }
)

function Get-Sha256([string] $Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-EnabledSyncJars([string] $ModsPath) {
    return @(
        Get-ChildItem -LiteralPath $ModsPath -File |
            Where-Object {
                $_.Extension -eq '.jar' -and
                $_.Name -match '(?i)mmdsync'
            }
    )
}

$clientProcesses = @(
    Get-CimInstance Win32_Process | Where-Object {
        $_.Name -match '^(javaw|minecraft).*\.exe$' -or
        $_.CommandLine -match '(?i)net\.fabricmc\.loader\.impl\.launch\.knot|cpw\.mods\.modlauncher|--gameDir'
    }
)
if ($clientProcesses.Count -ne 0) {
    $details = ($clientProcesses | ForEach-Object { "PID=$($_.ProcessId) $($_.Name)" }) -join '; '
    throw "Refusing deployment while a Minecraft client may be running: $details"
}

$timestamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
$records = New-Object System.Collections.Generic.List[object]

foreach ($deployment in $deployments) {
    $source = (Resolve-Path -LiteralPath $deployment.Source).Path
    $sourceHash = Get-Sha256 $source
    if ($sourceHash -ne $deployment.SourceHash) {
        throw "Source hash changed for $($deployment.Instance): $sourceHash"
    }

    $instanceRoot = (Resolve-Path -LiteralPath ("../HMCL/.minecraft/versions/" + $deployment.Instance)).Path
    $modsPath = Join-Path $instanceRoot 'mods'
    if (-not (Test-Path -LiteralPath $modsPath -PathType Container)) {
        throw "Missing mods directory for $($deployment.Instance)"
    }

    $enabledSyncJars = @(Get-EnabledSyncJars $modsPath)
    if ($enabledSyncJars.Count -ne 1) {
        throw "Expected exactly one enabled Sync JAR in $($deployment.Instance), found $($enabledSyncJars.Count)"
    }

    $target = Join-Path $modsPath $deployment.TargetName
    if ($enabledSyncJars[0].FullName -ne $target) {
        throw "Unexpected enabled Sync JAR in $($deployment.Instance): $($enabledSyncJars[0].Name)"
    }
    if (-not (Test-Path -LiteralPath $target -PathType Leaf)) {
        throw "Missing expected target JAR: $target"
    }

    $matchingMmdSkin = @(
        Get-ChildItem -LiteralPath $modsPath -File |
            Where-Object {
                $_.Extension -eq '.jar' -and
                $_.Name -match $deployment.RequiredMmdSkinPattern
            }
    )
    if ($matchingMmdSkin.Count -ne 1) {
        throw "Expected exactly one enabled compatible MMD Skin JAR in $($deployment.Instance), found $($matchingMmdSkin.Count)"
    }

    $backupDirectory = Join-Path $instanceRoot ("mmdsync-backups/" + $timestamp)
    New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
    $backupJar = Join-Path $backupDirectory $deployment.TargetName
    Copy-Item -LiteralPath $target -Destination $backupJar -Force

    $oldHash = Get-Sha256 $target
    $backupHash = Get-Sha256 $backupJar
    if ($backupHash -ne $oldHash) {
        throw "Backup verification failed for $($deployment.Instance)"
    }

    $manifest = @(
        "timestampUtc=$timestamp"
        "instance=$($deployment.Instance)"
        "target=$target"
        "backup=$backupJar"
        "oldSha256=$oldHash"
        "newSha256=$sourceHash"
        "source=$source"
        "mmdSkin=$($matchingMmdSkin[0].Name)"
    ) -join [Environment]::NewLine
    Set-Content -LiteralPath (Join-Path $backupDirectory 'deployment-manifest.txt') -Value $manifest -Encoding UTF8

    $records.Add([pscustomobject]@{
        Instance = $deployment.Instance
        Source = $source
        SourceHash = $sourceHash
        Target = $target
        OldHash = $oldHash
        BackupJar = $backupJar
        BackupDirectory = $backupDirectory
        MmdSkin = $matchingMmdSkin[0].Name
    })
}

$started = New-Object System.Collections.Generic.List[object]
try {
    foreach ($record in $records) {
        $targetDirectory = Split-Path -Parent $record.Target
        $targetName = Split-Path -Leaf $record.Target
        Get-ChildItem -LiteralPath $targetDirectory -File |
            Where-Object {
                $_.Name -like ".$targetName.deploying-*.tmp" -or
                $_.Name -like ".$targetName.replace-backup-*.bak"
            } |
            Remove-Item -Force

        $temp = Join-Path $targetDirectory ("." + $targetName + ".deploying-" + $PID + ".tmp")
        $replaceBackup = Join-Path $targetDirectory ("." + $targetName + ".replace-backup-" + $PID + ".bak")
        Copy-Item -LiteralPath $record.Source -Destination $temp -Force
        if ((Get-Sha256 $temp) -ne $record.SourceHash) {
            Remove-Item -LiteralPath $temp -Force -ErrorAction SilentlyContinue
            throw "Staged copy hash mismatch for $($record.Instance)"
        }

        $started.Add($record)
        [System.IO.File]::Replace($temp, $record.Target, $replaceBackup, $true)

        $deployedHash = Get-Sha256 $record.Target
        if ($deployedHash -ne $record.SourceHash) {
            throw "Deployed hash mismatch for $($record.Instance): $deployedHash"
        }
        Remove-Item -LiteralPath $replaceBackup -Force
    }

    foreach ($record in $records) {
        $modsPath = Split-Path -Parent $record.Target
        $enabledSyncJars = @(Get-EnabledSyncJars $modsPath)
        if ($enabledSyncJars.Count -ne 1 -or $enabledSyncJars[0].FullName -ne $record.Target) {
            throw "Post-deployment duplicate Sync JAR check failed for $($record.Instance)"
        }
        if ((Get-Sha256 $record.Target) -ne $record.SourceHash) {
            throw "Post-deployment hash check failed for $($record.Instance)"
        }
    }
}
catch {
    foreach ($record in $started) {
        Copy-Item -LiteralPath $record.BackupJar -Destination $record.Target -Force
    }
    throw "Deployment failed and touched targets were rolled back: $($_.Exception.Message)"
}

Write-Output "DEPLOYMENT_TIMESTAMP_UTC=$timestamp"
foreach ($record in $records) {
    Write-Output "INSTANCE=$($record.Instance)"
    Write-Output "TARGET=$($record.Target)"
    Write-Output "BACKUP=$($record.BackupJar)"
    Write-Output "MMDSKIN=$($record.MmdSkin)"
    Write-Output "OLD_SHA256=$($record.OldHash)"
    Write-Output "NEW_SHA256=$((Get-Sha256 $record.Target))"
    Write-Output "ENABLED_SYNC_JARS=$(@(Get-EnabledSyncJars (Split-Path -Parent $record.Target)).Count)"
}
