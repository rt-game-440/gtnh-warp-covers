# 向开发客户端发送带扫描码的按键（LWJGL 按扫描码读键盘，普通自动化工具发的键它收不到）。
# 输入法要切到英文。只操作标题为 Minecraft 1.7.10 的窗口。
# 用法：client-input.ps1 cmd "/tp 1 2 3"   打开聊天框、输入命令并回车
#       client-input.ps1 text "abc"        只输入文本
#       client-input.ps1 key 1B            按一个虚拟键码（十六进制）
#       client-input.ps1 look "-30,10"     相对转动视角
param([string]$Mode, [string]$Arg)
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class K {
  [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);
  [DllImport("user32.dll")] public static extern uint MapVirtualKey(uint code, uint type);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern short VkKeyScan(char c);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern void mouse_event(uint flags, int dx, int dy, uint data, UIntPtr extra);
  public static void Down(byte vk) { keybd_event(vk, (byte)MapVirtualKey(vk, 0), 0, UIntPtr.Zero); }
  public static void Up(byte vk) { keybd_event(vk, (byte)MapVirtualKey(vk, 0), 2, UIntPtr.Zero); }
}
"@
$p = Get-Process javaw, java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -eq 'Minecraft 1.7.10' } | Select-Object -First 1
if (-not $p) { Write-Error '找不到开发客户端窗口'; exit 1 }
[void][K]::SetForegroundWindow($p.MainWindowHandle)
Start-Sleep -Milliseconds 150

function Press([byte]$vk) { [K]::Down($vk); Start-Sleep -Milliseconds 40; [K]::Up($vk); Start-Sleep -Milliseconds 40 }
function Send-Text([string]$s) {
    foreach ($c in $s.ToCharArray()) {
        $r = [K]::VkKeyScan($c)
        $vk = [byte]($r -band 0xFF)
        $shift = (($r -shr 8) -band 1) -eq 1
        if ($shift) { [K]::Down(0x10) }
        Press $vk
        if ($shift) { [K]::Up(0x10) }
    }
}
switch ($Mode) {
    'cmd' { Press 0x54; Start-Sleep -Milliseconds 300; Send-Text $Arg; Start-Sleep -Milliseconds 100; Press 0x0D }
    'text' { Send-Text $Arg }
    'key' { Press ([byte][Convert]::ToInt32($Arg, 16)) }
    'look' { $v = $Arg -split ','; [K]::mouse_event(1, [int]$v[0], [int]$v[1], 0, [UIntPtr]::Zero) }
    default { Write-Error "未知模式 $Mode"; exit 1 }
}
