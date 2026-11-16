[CmdletBinding()]
param(
    [string]$PostgresBin = $env:POSTGRES_BIN,
    [string]$DataDirectory = (Join-Path $PSScriptRoot '..\data\postgres'),
    [string]$HostName = '127.0.0.1',
    [int]$Port = 5432,
    [string]$Database = 'card_origination_db',
    [string]$BootstrapUser = 'postgres',
    [string]$AdminUser = 'origination_admin',
    [string]$ServiceUser = 'origination_svc_user'
)

$ErrorActionPreference = 'Stop'
$DataDirectory = [System.IO.Path]::GetFullPath($DataDirectory)

if (-not $PostgresBin) {
    throw 'Set POSTGRES_BIN to the portable PostgreSQL bin directory containing initdb.exe, pg_ctl.exe, and psql.exe.'
}
foreach ($identifier in @($Database, $BootstrapUser, $AdminUser, $ServiceUser)) {
    if ($identifier -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
        throw "Invalid PostgreSQL identifier: $identifier"
    }
}
$PostgresBin = [System.IO.Path]::GetFullPath($PostgresBin)
$initdb = Join-Path $PostgresBin 'initdb.exe'
$pgCtl = Join-Path $PostgresBin 'pg_ctl.exe'
$psql = Join-Path $PostgresBin 'psql.exe'
foreach ($binary in @($initdb, $pgCtl, $psql)) {
    if (-not (Test-Path $binary -PathType Leaf)) {
        throw "Required PostgreSQL executable not found: $binary"
    }
}

function Read-Secret([string]$Prompt) {
    $secure = Read-Host -AsSecureString $Prompt
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

$bootstrapPassword = if ($env:POSTGRES_BOOTSTRAP_PASSWORD) { $env:POSTGRES_BOOTSTRAP_PASSWORD } else { Read-Secret 'Set password for PostgreSQL bootstrap superuser' }
$adminPassword = if ($env:ORIGINATION_ADMIN_PASSWORD) { $env:ORIGINATION_ADMIN_PASSWORD } else { Read-Secret 'Set password for origination_admin' }
$servicePassword = if ($env:ORIGINATION_SERVICE_PASSWORD) { $env:ORIGINATION_SERVICE_PASSWORD } else { Read-Secret 'Set password for origination_svc_user' }
if ($bootstrapPassword.Length -lt 16 -or $adminPassword.Length -lt 16 -or $servicePassword.Length -lt 16) {
    throw 'Database passwords must be at least 16 characters.'
}
if ($bootstrapPassword -eq $adminPassword -or $bootstrapPassword -eq $servicePassword -or $adminPassword -eq $servicePassword) {
    throw 'Use distinct passwords for the bootstrap, migration-owner, and runtime accounts.'
}

New-Item -ItemType Directory -Force -Path $DataDirectory | Out-Null
$passwordFile = Join-Path ([System.IO.Path]::GetTempPath()) ([Guid]::NewGuid().ToString() + '.pgpass')
$bootstrapSql = Join-Path ([System.IO.Path]::GetTempPath()) ([Guid]::NewGuid().ToString() + '.sql')
$previousPgPassword = $env:PGPASSWORD
function Protect-CurrentUserFile([string]$Path) {
    $currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $acl = Get-Acl -LiteralPath $Path
    $acl.SetAccessRuleProtection($true, $false)
    $rule = New-Object System.Security.AccessControl.FileSystemAccessRule(
        $currentUser, [System.Security.AccessControl.FileSystemRights]::FullControl,
        [System.Security.AccessControl.AccessControlType]::Allow)
    $acl.SetAccessRule($rule)
    Set-Acl -LiteralPath $Path -AclObject $acl
}
try {
    [System.IO.File]::WriteAllText($passwordFile, $bootstrapPassword, (New-Object System.Text.UTF8Encoding($false)))
    Protect-CurrentUserFile $passwordFile
    if (-not (Test-Path (Join-Path $DataDirectory 'PG_VERSION'))) {
        & $initdb -D $DataDirectory -U $BootstrapUser --pwfile=$passwordFile --auth=scram-sha-256 --encoding=UTF8
        if ($LASTEXITCODE -ne 0) { throw "initdb failed with exit code $LASTEXITCODE" }
    } else {
        Write-Host "Using existing PostgreSQL data directory: $DataDirectory"
    }

    & $pgCtl -D $DataDirectory status *> $null
    if ($LASTEXITCODE -ne 0) {
        & $pgCtl -D $DataDirectory -o "-h $HostName -p $Port" -l (Join-Path $DataDirectory 'postgresql.log') start
        if ($LASTEXITCODE -ne 0) { throw "pg_ctl start failed with exit code $LASTEXITCODE" }
    }

    $escapedServicePassword = $servicePassword.Replace("'", "''")
    $escapedAdminPassword = $adminPassword.Replace("'", "''")
    $sql = @"
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD %L', '$ServiceUser', '$escapedServicePassword')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$ServiceUser') \gexec
SELECT format('ALTER ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD %L', '$ServiceUser', '$escapedServicePassword') \gexec
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD %L', '$AdminUser', '$escapedAdminPassword')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$AdminUser') \gexec
SELECT format('ALTER ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD %L', '$AdminUser', '$escapedAdminPassword') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', '$Database', '$AdminUser')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = '$Database') \gexec
ALTER DATABASE "$Database" OWNER TO "$AdminUser";
REVOKE ALL ON DATABASE "$Database" FROM PUBLIC;
GRANT CONNECT ON DATABASE "$Database" TO "$ServiceUser";
"@
    [System.IO.File]::WriteAllText($bootstrapSql, $sql, (New-Object System.Text.UTF8Encoding($false)))
    Protect-CurrentUserFile $bootstrapSql
    $env:PGPASSWORD = $bootstrapPassword
    & $psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $BootstrapUser -d postgres -f $bootstrapSql
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL role/database bootstrap failed with exit code $LASTEXITCODE" }

    $dbSql = @"
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO "$ServiceUser";
"@
    [System.IO.File]::WriteAllText($bootstrapSql, $dbSql, (New-Object System.Text.UTF8Encoding($false)))
    Protect-CurrentUserFile $bootstrapSql
    $env:PGPASSWORD = $adminPassword
    & $psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $AdminUser -d $Database -f $bootstrapSql
    if ($LASTEXITCODE -ne 0) { throw "Schema privilege bootstrap failed with exit code $LASTEXITCODE" }

    $env:FLYWAY_URL = "jdbc:postgresql://${HostName}:$Port/$Database"
    $env:FLYWAY_USER = $AdminUser
    $env:FLYWAY_PASSWORD = $adminPassword
    $env:DATABASE_USERNAME = $ServiceUser
    $env:DATABASE_PASSWORD = $servicePassword
    Push-Location (Join-Path $PSScriptRoot '..')
    try {
        & .\gradlew.bat :origination-api:flywayMigrate
        if ($LASTEXITCODE -ne 0) { throw "Flyway migration failed with exit code $LASTEXITCODE" }
    } finally { Pop-Location }
    Write-Host "PostgreSQL is ready. Database: $Database; runtime user: $ServiceUser (DML only)."
} finally {
    Remove-Item -LiteralPath $passwordFile, $bootstrapSql -Force -ErrorAction SilentlyContinue
    if ($null -eq $previousPgPassword) {
        Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    } else {
        $env:PGPASSWORD = $previousPgPassword
    }
}
