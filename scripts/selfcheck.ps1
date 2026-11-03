# 一键服务器自检：启动开发服务器 -> 布置 -> 停服 -> 重启 -> 校验 -> 停服 -> 读取结果。
# 用法: ./scripts/selfcheck.ps1 [-Scenario gt_hull_smoke] [-TimeoutSec 600]
# 退出码：0 = 全部通过，1 = 有失败或超时。结果行来自 logs/yyyy-MM-dd HH-mm-ss.log 中的 [RESULT] 行。
param(
    [string]$Scenario = 'gt_hull_smoke',
    [int]$TimeoutSec = 600
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$serverDir = Join-Path $root 'run\server'
$logsDir = Join-Path $root 'logs'
$stamp = Get-Date -Format 'yyyy-MM-dd HH-mm-ss'

New-Item -ItemType Directory -Force $serverDir, $logsDir | Out-Null
# 开发用服务器：接受 EULA、关闭正版验证
Set-Content -Path (Join-Path $serverDir 'eula.txt') -Value 'eula=true' -Encoding ASCII
$props = Join-Path $serverDir 'server.properties'
if (-not (Test-Path $props)) {
    Set-Content -Path $props -Value "online-mode=false`nlevel-type=FLAT" -Encoding ASCII
}
# 状态文件由命令写在游戏目录，每次完整自检从干净状态开始
Remove-Item -Force -ErrorAction SilentlyContinue (Join-Path $serverDir 'warpcovers-selfcheck-state.txt')

$before = @(Get-ChildItem $logsDir -Filter '*.log' -ErrorAction SilentlyContinue | ForEach-Object Name)

function Invoke-ServerPhase([string]$Phase, [string]$Command, [string]$WaitFor) {
    Write-Host "== 阶段 $Phase：启动开发服务器 =="
    # 每个阶段一个控制台日志；cmd 负责把服务器输出重定向到文件，脚本只轮询文件，stdin 留给脚本发命令
    $phaseLog = Join-Path $logsDir ("console $stamp " + $Phase.Substring(0, 3).Replace('/', 'of') + '.txt')
    Set-Content -Path $phaseLog -Value '' -Encoding UTF8
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $env:ComSpec
    $psi.Arguments = '/c ""' + (Join-Path $root 'gradlew.bat') + '" runServer --console=plain > "' + $phaseLog + '" 2>&1"'
    $psi.WorkingDirectory = $root
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $psi
    $null = $proc.Start()

    function Wait-Output([string]$Pattern, [string]$What) {
        $deadline = (Get-Date).AddSeconds($TimeoutSec)
        while ($true) {
            $text = ''
            try {
                $fs = [System.IO.File]::Open($phaseLog, 'Open', 'Read', 'ReadWrite')
                try { $text = (New-Object System.IO.StreamReader($fs, [System.Text.Encoding]::UTF8)).ReadToEnd() } finally { $fs.Dispose() }
            }
            catch { }
            if ($text -match $Pattern) { break }
            if ($proc.HasExited) { throw "服务器在等待「$What」时已退出，见 $phaseLog" }
            if ((Get-Date) -gt $deadline) { throw "等待「$What」超时，见 $phaseLog" }
            Start-Sleep -Milliseconds 500
        }
    }

    try {
        Wait-Output 'Done \(' '服务器启动完成'
        Write-Host "发送: $Command"
        $proc.StandardInput.WriteLine($Command)
        Wait-Output ([regex]::Escape($WaitFor)) "命令回显 $WaitFor"
        $proc.StandardInput.WriteLine('stop')
        if (-not $proc.WaitForExit($TimeoutSec * 1000)) { throw '停服超时' }
    }
    finally {
        if (-not $proc.HasExited) { & taskkill /PID $proc.Id /T /F | Out-Null }
    }
}

$failed = $false
try {
    Invoke-ServerPhase '1/2 布置' "warpcovers_selfcheck setup $Scenario" "[WarpCovers 自检] setup $Scenario"
    Invoke-ServerPhase '2/2 校验（重启后）' "warpcovers_selfcheck verify $Scenario" "[WarpCovers 自检] verify $Scenario"
}
catch {
    Write-Host "自检流程异常: $_" -ForegroundColor Red
    $failed = $true
}

$results = @()
foreach ($f in Get-ChildItem $logsDir -Filter '*.log' -ErrorAction SilentlyContinue) {
    if ($before -notcontains $f.Name) {
        $results += Get-Content $f.FullName -Encoding UTF8 | Where-Object { $_ -like '[[]RESULT]*' }
    }
}
Write-Host '== 自检结果 =='
$results | ForEach-Object { Write-Host $_ }

# 启动日志里不允许出现本模组（modid warpcovers）的 ERROR/FATAL
$modErrors = @()
foreach ($f in Get-ChildItem $logsDir -Filter "console $stamp *.txt" -ErrorAction SilentlyContinue) {
    $modErrors += Get-Content $f.FullName -Encoding UTF8 | Where-Object { $_ -match '/(ERROR|FATAL)\] \[warpcovers\]' }
}
if ($modErrors.Count -gt 0) {
    Write-Host '== 启动日志中的本模组报错 ==' -ForegroundColor Red
    $modErrors | ForEach-Object { Write-Host $_ }
    $failed = $true
}

$setupOk = @($results | Where-Object { $_ -like "*setup $Scenario PASS*" }).Count -ge 1
$verifyOk = @($results | Where-Object { $_ -like "*verify $Scenario PASS*" }).Count -ge 1
if ($failed -or -not ($setupOk -and $verifyOk)) {
    Write-Host '自检失败' -ForegroundColor Red
    exit 1
}
Write-Host '自检通过' -ForegroundColor Green
exit 0
