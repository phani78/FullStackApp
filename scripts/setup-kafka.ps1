[CmdletBinding()]
param(
    [string]$KafkaHome = $env:KAFKA_HOME,
    [string]$DataDirectory = (Join-Path $PSScriptRoot '..\data\kafka'),
    [string]$HostName = '127.0.0.1',
    [int]$BrokerPort = 9092,
    [int]$ControllerPort = 9093
)

$ErrorActionPreference = 'Stop'
if (-not $KafkaHome) {
    throw 'Set KAFKA_HOME to the extracted native Kafka distribution directory.'
}
$KafkaHome = [System.IO.Path]::GetFullPath($KafkaHome)
$DataDirectory = [System.IO.Path]::GetFullPath($DataDirectory)
$storageTool = Join-Path $KafkaHome 'bin\windows\kafka-storage.bat'
$serverTool = Join-Path $KafkaHome 'bin\windows\kafka-server-start.bat'
if (-not (Test-Path $storageTool -PathType Leaf) -or -not (Test-Path $serverTool -PathType Leaf)) {
    throw 'Kafka Windows scripts were not found under KAFKA_HOME\bin\windows.'
}

$logDirectory = Join-Path $DataDirectory 'logs'
$metadataDirectory = Join-Path $DataDirectory 'metadata'
$configPath = Join-Path $DataDirectory 'server.properties'
New-Item -ItemType Directory -Force -Path $DataDirectory, $logDirectory, $metadataDirectory | Out-Null
$escapedLogDirectory = $logDirectory.Replace('\', '/')
$escapedMetadataDirectory = $metadataDirectory.Replace('\', '/')
$config = @"
process.roles=broker,controller
node.id=1
controller.quorum.voters=1@${HostName}:${ControllerPort}
listeners=PLAINTEXT://${HostName}:${BrokerPort},CONTROLLER://${HostName}:${ControllerPort}
advertised.listeners=PLAINTEXT://${HostName}:${BrokerPort}
controller.listener.names=CONTROLLER
inter.broker.listener.name=PLAINTEXT
listener.security.protocol.map=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
log.dirs=$escapedLogDirectory
metadata.log.dir=$escapedMetadataDirectory
num.partitions=1
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
group.initial.rebalance.delay.ms=0
"@
[System.IO.File]::WriteAllText($configPath, $config, (New-Object System.Text.UTF8Encoding($false)))

$metadataFile = Join-Path $metadataDirectory 'meta.properties'
if (-not (Test-Path $metadataFile)) {
    $randomUuidTool = Join-Path $KafkaHome 'bin\windows\kafka-storage.bat'
    $uuidOutput = & $env:ComSpec /c "`"$randomUuidTool`" random-uuid" 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Kafka failed to generate a KRaft cluster ID.' }
    $clusterId = ($uuidOutput | Where-Object { $_ -match '^[A-Za-z0-9_-]+$' } | Select-Object -Last 1)
    if (-not $clusterId) { throw 'Kafka did not return a valid KRaft cluster ID.' }
    & $env:ComSpec /c "`"$storageTool`" format -t $clusterId -c `"$configPath`""
    if ($LASTEXITCODE -ne 0) { throw "Kafka KRaft storage formatting failed with exit code $LASTEXITCODE" }
    Write-Host "Kafka KRaft storage initialized at $DataDirectory."
} else {
    Write-Host "Kafka KRaft storage already exists at $DataDirectory."
}
Write-Host "Start Kafka in a separate PowerShell window with:"
Write-Host "& `"$serverTool`" `"$configPath`""
