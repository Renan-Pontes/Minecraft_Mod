param([int]$X, [int]$Y, [switch]$Right, [int]$Scroll = 0)
# Clique (ou rolagem) na janela do Minecraft de desenvolvimento via PostMessage (não precisa de foco).
# X/Y em pixels da CAPTURA do shot.ps1 (janela inteira); converte para coordenadas da área cliente.
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class W32c {
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
  [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
}
"@
[W32c]::SetProcessDPIAware() | Out-Null
$p = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*fml.modFolders*' -and (Get-Process -Id $_.ProcessId).MainWindowHandle -ne 0 } | Select-Object -First 1
if (-not $p) { Write-Output "sem processo"; exit 1 }
$h = (Get-Process -Id $p.ProcessId).MainWindowHandle
$r = New-Object W32c+RECT; [W32c]::GetWindowRect($h, [ref]$r) | Out-Null
$o = New-Object W32c+POINT; [W32c]::ClientToScreen($h, [ref]$o) | Out-Null
$cx = $X - ($o.X - $r.L); $cy = $Y - ($o.Y - $r.T)
$l = [IntPtr](($cy -shl 16) -bor ($cx -band 0xFFFF))
[W32c]::PostMessage($h, 0x0200, [IntPtr]0, $l) | Out-Null   # mover
Start-Sleep -Milliseconds 120
if ($Scroll -ne 0) {
  $sp = New-Object W32c+POINT; $sp.X = $cx; $sp.Y = $cy; [W32c]::ClientToScreen($h, [ref]$sp) | Out-Null
  $wl = [IntPtr](($sp.Y -shl 16) -bor ($sp.X -band 0xFFFF))
  [W32c]::PostMessage($h, 0x020A, [IntPtr]((120 * $Scroll) -shl 16), $wl) | Out-Null
} elseif ($Right) {
  [W32c]::PostMessage($h, 0x0204, [IntPtr]2, $l) | Out-Null; Start-Sleep -Milliseconds 80; [W32c]::PostMessage($h, 0x0205, [IntPtr]0, $l) | Out-Null
} else {
  [W32c]::PostMessage($h, 0x0201, [IntPtr]1, $l) | Out-Null; Start-Sleep -Milliseconds 80; [W32c]::PostMessage($h, 0x0202, [IntPtr]0, $l) | Out-Null
}
Start-Sleep -Milliseconds 400
Write-Output "clique $cx,$cy"
