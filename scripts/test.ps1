# 一键运行 JUnit 单元测试。用法: ./scripts/test.ps1 [额外的 gradle 参数，如 --tests *SelfCheck*]
# 退出码：0 = 全部通过，非 0 = 有失败。
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    & "$root\gradlew.bat" test --console=plain @args
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
