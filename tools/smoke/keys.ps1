param([string[]]$Seq)
# Envia teclas para a janela do Minecraft de desenvolvimento via PostMessage (não precisa de foco).
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class W32k {
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
  [DllImport("user32.dll")] public static extern uint MapVirtualKey(uint code, uint type);
}
"@
$p = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*fml.modFolders*' -and (Get-Process -Id $_.ProcessId).MainWindowHandle -ne 0 } | Select-Object -First 1
if (-not $p) { Write-Output "sem processo"; exit 1 }
$h = (Get-Process -Id $p.ProcessId).MainWindowHandle
$vk = @{ 'TAB'=0x09; 'ENTER'=0x0D; 'ESC'=0x1B; 'F2'=0x71; 'F1'=0x70; 'F3'=0x72; 'B'=0x42; 'Z'=0x5A; 'G'=0x47; 'T'=0x54; 'RIGHT'=0x27; 'LEFT'=0x25; 'SPACE'=0x20; 'DOWN'=0x28; 'UP'=0x26; 'F5'=0x74; 'E'=0x45; 'S'=0x53; 'W'=0x57; '1'=0x31; '2'=0x32; 'BACK'=0x08; 'C'=0x43; 'X'=0x58; 'J'=0x4A; 'CTRLA'=0x41 }
foreach ($k in $Seq) {
  if ($k -like 'wait*') { Start-Sleep -Milliseconds ([int]$k.Substring(4)); continue }
  if ($k -like 'type:*') {
    foreach ($ch in $k.Substring(5).ToCharArray()) { [W32k]::PostMessage($h, 0x0102, [IntPtr][int]$ch, [IntPtr]1) | Out-Null; Start-Sleep -Milliseconds 15 }
    Start-Sleep -Milliseconds 200; Write-Output "digitou"; continue
  }
  $code = [uint32]$vk[$k]
  $scan = [W32k]::MapVirtualKey($code, 0)
  $ext = 0
  if (@(0x25,0x26,0x27,0x28) -contains $code) { $ext = 1 }
  $down = [IntPtr](1 -bor ($scan -shl 16) -bor ($ext -shl 24))
  $up = [IntPtr]([int64](1 -bor ($scan -shl 16) -bor ($ext -shl 24)) -bor 0xC0000000)
  [W32k]::PostMessage($h, 0x0100, [IntPtr]$code, $down) | Out-Null
  Start-Sleep -Milliseconds 90
  [W32k]::PostMessage($h, 0x0101, [IntPtr]$code, $up) | Out-Null
  Start-Sleep -Milliseconds 300
  Write-Output "tecla $k"
}
