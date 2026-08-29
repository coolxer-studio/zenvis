param(
    [string]$PluginRoot
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$RepoDir = $PSScriptRoot
$ConfigPath = Join-Path $RepoDir "build.conf"

if (-not (Test-Path $ConfigPath)) {
    throw "缺少构建配置：$ConfigPath"
}

$Config = @{}
Get-Content $ConfigPath -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $Config[$matches[1]] = $matches[2].Trim()
    }
}

$PluginDir = $Config["PLUGIN_DIR"]
$ApiDir = $Config["API_DIR"]
$ApiArtifact = $Config["API_ARTIFACT"]
if (-not $PluginDir) { throw "build.conf 必须声明 PLUGIN_DIR" }
if (-not $PluginRoot) { $PluginRoot = $PluginDir }
if ($PluginRoot -ne $PluginDir) {
    throw "当前仓库只支持构建 $PluginDir，收到：$PluginRoot"
}

$PluginPath = Join-Path $RepoDir $PluginDir
$IndexPath = Join-Path $PluginPath "index.json"
if (-not (Test-Path $IndexPath)) { throw "插件描述不存在：$IndexPath" }
$Index = Get-Content $IndexPath -Raw -Encoding UTF8 | ConvertFrom-Json
$PluginVersion = [string]$Index.version
$PackageName = [string]$Index.package_name
if (-not $PluginVersion -or -not $PackageName) {
    throw "无法从 index.json 读取 version 或 package_name"
}

if ($ApiDir) {
    $ApiPath = Join-Path $RepoDir $ApiDir
    $PomPath = Join-Path $ApiPath "pom.xml"
    if (-not (Test-Path $PomPath) -or -not $ApiArtifact) {
        throw "API 工程配置不完整：$ApiPath"
    }
    [xml]$Pom = Get-Content $PomPath -Raw -Encoding UTF8
    $ApiVersion = [string]$Pom.project.version
    if ($ApiVersion -ne $PluginVersion) {
        throw "版本不一致：index.json=$PluginVersion，$ApiDir/pom.xml=$ApiVersion"
    }

    Write-Host "构建动态 API：${ApiArtifact}:${ApiVersion}"
    & mvn -f $PomPath clean package
    if ($LASTEXITCODE -ne 0) { throw "Maven 构建失败：$ApiArtifact" }

    $BuiltJar = Join-Path $ApiPath "target/$ApiArtifact-$ApiVersion.jar"
    if (-not (Test-Path $BuiltJar)) { throw "API 产物不存在：$BuiltJar" }
    $JarEntries = & jar tf $BuiltJar
    if ($LASTEXITCODE -ne 0) { throw "无法读取 JAR：$BuiltJar" }
    if ($JarEntries -match '^BOOT-INF/') { throw "动态 API 必须是薄 JAR，不能包含 BOOT-INF" }
    if (-not ($JarEntries -match '^com/coolxer/plugin/.+\.class$')) {
        throw "动态 API JAR 未包含 com.coolxer.plugin 业务类"
    }

    $ApiTarget = Join-Path $PluginPath "03_api"
    New-Item -ItemType Directory -Path $ApiTarget -Force | Out-Null
    Get-ChildItem $ApiTarget -Filter "*.jar" -File -ErrorAction SilentlyContinue | Remove-Item -Force
    Copy-Item $BuiltJar $ApiTarget -Force
}

$ApiTarget = Join-Path $PluginPath "03_api"
$JarCount = @(Get-ChildItem $ApiTarget -Filter "*.jar" -File -ErrorAction SilentlyContinue).Count
if ($JarCount -gt 1) { throw "03_api 根目录最多允许一个 JAR，当前为 $JarCount" }

$ArchiveName = ($PackageName -replace '\.', '-') + ".tar.gz"
$ArchivePath = Join-Path $RepoDir $ArchiveName
if (Test-Path $ArchivePath) { Remove-Item $ArchivePath -Force }

$PackageEntries = @(
    Get-ChildItem -LiteralPath $PluginPath -Force | Where-Object {
        $_.Name -notin @(".DS_Store", ".git", "api-src", "target", "build.log") -and
        $_.Name -notlike "*.tar" -and
        $_.Name -notlike "*.tar.gz"
    } | ForEach-Object { $_.Name }
)
if ($PackageEntries.Count -eq 0) { throw "插件目录没有可打包内容：$PluginPath" }

$TarArguments = @(
    "-czf", $ArchivePath,
    "--exclude=.DS_Store",
    "--exclude=.git",
    "--exclude=api-src",
    "--exclude=target",
    "--exclude=build.log",
    "--exclude=*.tar",
    "--exclude=*.tar.gz",
    "-C", $PluginPath
) + $PackageEntries
& tar @TarArguments
if ($LASTEXITCODE -ne 0) { throw "创建归档失败：$ArchivePath" }

$ArchiveEntries = & tar -tzf $ArchivePath
if ($LASTEXITCODE -ne 0) { throw "读取归档失败：$ArchivePath" }
if ($ArchiveEntries -notcontains "index.json") { throw "归档根目录缺少 index.json" }
if (@($ArchiveEntries | Where-Object {
    $_ -eq "." -or $_ -eq "./" -or $_.StartsWith("./")
}).Count -gt 0) {
    throw "归档包含 ZenVis 不接受的点目录条目"
}
if ($ArchiveEntries -match '(^|/)(\.git|api-src|target)(/|$)|(^|/)\.DS_Store$|build\.log$|\.tar\.gz$') {
    throw "归档包含不允许发布的文件"
}

Write-Host "构建完成：$ArchivePath"
