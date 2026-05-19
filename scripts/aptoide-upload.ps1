[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
    [string]$ApkDirectory,
    [ValidateSet('none', 'aptoide-sdk')]
    [string]$BillingMethod = $(if ($env:APTOIDE_BILLING_METHOD) { $env:APTOIDE_BILLING_METHOD } else { 'none' }),
    [ValidateSet('IMMEDIATE', 'MANUAL', 'SCHEDULED')]
    [string]$ReleaseMode = $(if ($env:APTOIDE_RELEASE_MODE) { $env:APTOIDE_RELEASE_MODE } else { 'MANUAL' }),
    [string]$ReleaseTimestamp = $env:APTOIDE_RELEASE_TIMESTAMP,
    [bool]$RequiresDeveloperApproval = $(if ($env:APTOIDE_REQUIRES_DEVELOPER_APPROVAL) { [System.Convert]::ToBoolean($env:APTOIDE_REQUIRES_DEVELOPER_APPROVAL) } else { $true }),
    [switch]$UsesAptoideBillingPublicKey,
    [string]$Endpoint = 'https://uploader.catappult.io/api'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptRoot = if (-not [string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    $PSScriptRoot
}
else {
    Split-Path -Parent $MyInvocation.MyCommand.Path
}

if ([string]::IsNullOrWhiteSpace($ApkDirectory)) {
    $ApkDirectory = Join-Path $scriptRoot '..\android\app\build\outputs\apk\release'
}

function Format-FileSize {
    param([long]$Bytes)

    if ($Bytes -ge 1GB) {
        return '{0:N2} GB' -f ($Bytes / 1GB)
    }

    if ($Bytes -ge 1MB) {
        return '{0:N2} MB' -f ($Bytes / 1MB)
    }

    if ($Bytes -ge 1KB) {
        return '{0:N2} KB' -f ($Bytes / 1KB)
    }

    return "$Bytes bytes"
}

function Read-ErrorBody {
    param($ErrorRecord)

    $response = $ErrorRecord.Exception.Response
    if ($null -eq $response) {
        return $null
    }

    try {
        if ($response -is [System.Net.Http.HttpResponseMessage]) {
            return $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        }

        $stream = $response.GetResponseStream()
        if ($null -eq $stream) {
            return $null
        }

        $reader = [System.IO.StreamReader]::new($stream)
        try {
            return $reader.ReadToEnd()
        }
        finally {
            $reader.Dispose()
        }
    }
    catch {
        return $null
    }
}

function Get-StatusCode {
    param($ErrorRecord)

    $response = $ErrorRecord.Exception.Response
    if ($null -eq $response) {
        return $null
    }

    if ($response.StatusCode -is [int]) {
        return [int]$response.StatusCode
    }

    return [int]$response.StatusCode.value__
}

function New-MultipartFormDataBody {
    param(
        [hashtable]$Fields,
        [System.IO.FileInfo]$ApkFile
    )

    $boundary = "----GravityRunnerAptoide$([System.Guid]::NewGuid().ToString('N'))"
    $stream = [System.IO.MemoryStream]::new()

    function Write-Utf8 {
        param(
            [System.IO.Stream]$Target,
            [string]$Value
        )

        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
        $Target.Write($bytes, 0, $bytes.Length)
    }

    foreach ($fieldName in $Fields.Keys) {
        Write-Utf8 -Target $stream -Value "--$boundary`r`n"
        Write-Utf8 -Target $stream -Value "Content-Disposition: form-data; name=`"$fieldName`"`r`n`r`n"
        Write-Utf8 -Target $stream -Value "$($Fields[$fieldName])`r`n"
    }

    Write-Utf8 -Target $stream -Value "--$boundary`r`n"
    Write-Utf8 -Target $stream -Value "Content-Disposition: form-data; name=`"apk`"; filename=`"$($ApkFile.Name)`"`r`n"
    Write-Utf8 -Target $stream -Value "Content-Type: application/vnd.android.package-archive`r`n`r`n"

    $fileStream = [System.IO.File]::OpenRead($ApkFile.FullName)
    try {
        $fileStream.CopyTo($stream)
    }
    finally {
        $fileStream.Dispose()
    }

    Write-Utf8 -Target $stream -Value "`r`n--$boundary--`r`n"

    return @{
        Boundary = $boundary
        Body = $stream.ToArray()
    }
}

$resolvedApkDirectory = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ApkDirectory)

if (-not (Test-Path -LiteralPath $resolvedApkDirectory -PathType Container)) {
    throw "APK output directory was not found: $resolvedApkDirectory"
}

$unsupportedFiles = Get-ChildItem -LiteralPath $resolvedApkDirectory -File |
    Where-Object { $_.Extension -in @('.aab', '.zip', '.apks') } |
    Sort-Object LastWriteTime -Descending

$apkFiles = Get-ChildItem -LiteralPath $resolvedApkDirectory -File -Filter '*.apk' |
    Sort-Object LastWriteTime -Descending

if (-not $apkFiles) {
    if ($unsupportedFiles) {
        $names = ($unsupportedFiles | ForEach-Object { $_.Name }) -join ', '
        throw "No APK found. Refusing to upload unsupported app package file(s): $names"
    }

    throw "No APK found in $resolvedApkDirectory"
}

$apk = $apkFiles[0]
if ($apk.Extension -ne '.apk') {
    throw "Selected file is not an APK: $($apk.FullName)"
}

if ($ReleaseMode -eq 'SCHEDULED' -and [string]::IsNullOrWhiteSpace($ReleaseTimestamp)) {
    throw 'APTOIDE_RELEASE_TIMESTAMP or -ReleaseTimestamp is required when releaseMode is SCHEDULED. Expected UTC format: YYYY-MM-DD HH:mm:ss'
}

$usesBillingPublicKey = $UsesAptoideBillingPublicKey.IsPresent
if ($env:APTOIDE_USES_BILLING_PUBLIC_KEY) {
    $usesBillingPublicKey = [System.Convert]::ToBoolean($env:APTOIDE_USES_BILLING_PUBLIC_KEY)
}

if (($BillingMethod -eq 'aptoide-sdk' -or $usesBillingPublicKey) -and [string]::IsNullOrWhiteSpace($env:APTOIDE_PUBLIC_KEY)) {
    throw 'APTOIDE_PUBLIC_KEY is required because Aptoide billing public key usage was requested. The script checks presence only and never prints it.'
}

Write-Host "Aptoide Uploader API endpoint: $Endpoint"
Write-Host "APK path: $($apk.FullName)"
Write-Host "APK size: $(Format-FileSize -Bytes $apk.Length)"
Write-Host "billingMethod: $BillingMethod"
Write-Host "releaseMode: $ReleaseMode"
Write-Host "requiresDeveloperApproval: $RequiresDeveloperApproval"

if ($unsupportedFiles) {
    $unsupportedNames = ($unsupportedFiles | ForEach-Object { $_.Name }) -join ', '
    Write-Warning "Ignoring unsupported file(s) in release directory because an APK was found: $unsupportedNames"
}

if ($WhatIfPreference) {
    Write-Host 'WhatIf: no API key was read and no HTTP request was sent.'
    return
}

if ([string]::IsNullOrWhiteSpace($env:APTOIDE_API_KEY)) {
    throw 'APTOIDE_API_KEY is required for upload. Set it as an environment variable; the script will not print it.'
}

$confirmation = Read-Host "Type UPLOAD to submit this signed APK to Aptoide Connect"
if ($confirmation -cne 'UPLOAD') {
    throw 'Upload cancelled before HTTP POST.'
}

if (-not $PSCmdlet.ShouldProcess($apk.FullName, "POST multipart/form-data to $Endpoint")) {
    return
}

$headers = @{
    'Api-Key' = $env:APTOIDE_API_KEY
}

$fields = @{
    billingMethod = $BillingMethod
    releaseMode = $ReleaseMode
    requiresDeveloperApproval = $RequiresDeveloperApproval.ToString().ToLowerInvariant()
}

if ($ReleaseMode -eq 'SCHEDULED') {
    $fields.releaseTimestamp = $ReleaseTimestamp
}

# TODO: Only add app/package metadata after Aptoide documents exact request fields.
# Known Gravity Runner metadata for manual dashboard entry:
# Package: fun.dmtools.gravityrunne
# Title: Gravity Runner
# Privacy policy: https://dmtools.fun/gravity-runner-privacy.html
# Support email: admin@dmtools.fun
# Website: https://dmtools.fun

try {
    $multipart = New-MultipartFormDataBody -Fields $fields -ApkFile $apk
    $contentType = "multipart/form-data; boundary=$($multipart.Boundary)"
    $response = Invoke-WebRequest -Method Post -Uri $Endpoint -Headers $headers -ContentType $contentType -Body $multipart.Body
    Write-Host 'Aptoide upload request completed with HTTP 200.'
    if ($null -ne $response) {
        if ($response.Content) {
            $response.Content
        }
        else {
            $response | ConvertTo-Json -Depth 10
        }
    }
}
catch {
    $statusCode = Get-StatusCode -ErrorRecord $_
    $body = Read-ErrorBody -ErrorRecord $_

    if ($null -ne $statusCode) {
        Write-Error "Aptoide API request failed with HTTP status code $statusCode."
    }
    else {
        Write-Error "Aptoide API request failed: $($_.Exception.Message)"
    }

    if (-not [string]::IsNullOrWhiteSpace($body)) {
        Write-Error "Aptoide API response body: $body"
    }

    throw
}
