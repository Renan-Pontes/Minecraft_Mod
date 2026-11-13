param([string]$Mode = 'park', [string]$Out = '', [string]$Arg = '')
# Janela do cliente de TESTE (java.exe do gradle; nunca o javaw do TLauncher). Nada aqui ativa a janela nem usa o
# mouse/teclado de verdade: o jogo do usuário continua em foco.
#  park        : espera a janela aparecer e a move para FORA da tela (SetWindowPos, sem ativar)
#  shot        : captura a janela (PrintWindow) num PNG
#  key  <vk>   : aperta e solta uma tecla por mensagem (ex.: 27 = Esc, 66 = B)
#  click <x,y> : clique esquerdo por mensagem nas coordenadas da área cliente
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System; using System.Runtime.InteropServices;
public class WP {
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
  [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int cx, int cy, uint f);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
  [DllImport("user32.dll")] public static extern uint MapVirtualKey(uint code, uint type);
}
"@
[WP]::SetProcessDPIAware() | Out-Null
function Find { Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -like 'Minecraft*' } | Select-Object -First 1 }
function Park($h) { [WP]::SetWindowPos($h, [IntPtr]1, 2400, 60, 0, 0, 0x0011) | Out-Null }   # HWND_BOTTOM, NOSIZE|NOACTIVATE
if ($Mode -eq 'park') {
    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline) {
        $x = Find
        if ($x) { 1..12 | ForEach-Object { Park $x.MainWindowHandle; Start-Sleep -Milliseconds 250 }; "parked pid " + $x.Id + " :: " + $x.MainWindowTitle; exit 0 }
        Start-Sleep -Milliseconds 100
    }
    "sem janela"; exit 1
}
$x = Find
if (-not $x) { "sem processo"; exit 1 }
$h = $x.MainWindowHandle
if ($Mode -eq 'key') {
    foreach ($k in $Arg.Split(',')) {
        $vk = [uint32]$k; $sc = [WP]::MapVirtualKey($vk, 0)
        [WP]::PostMessage($h, 0x0100, [IntPtr]$vk, [IntPtr](1 -bor ($sc -shl 16))) | Out-Null
        Start-Sleep -Milliseconds 90
        [WP]::PostMessage($h, 0x0101, [IntPtr]$vk, [IntPtr]([int64]1 -bor ($sc -shl 16) -bor 0xC0000000)) | Out-Null
        Start-Sleep -Milliseconds 250
    }
    "keys $Arg"; exit 0
}
if ($Mode -eq 'click') {
    $xy = $Arg.Split(','); $l = [IntPtr]([int]$xy[0] -bor ([int]$xy[1] -shl 16))
    [WP]::PostMessage($h, 0x0200, [IntPtr]0, $l) | Out-Null; Start-Sleep -Milliseconds 120
    [WP]::PostMessage($h, 0x0201, [IntPtr]1, $l) | Out-Null; Start-Sleep -Milliseconds 90
    [WP]::PostMessage($h, 0x0202, [IntPtr]0, $l) | Out-Null
    "click $Arg"; exit 0
}
$r = New-Object WP+RECT
[WP]::GetWindowRect($h, [ref]$r) | Out-Null
$w = $r.R - $r.L; $hh = $r.B - $r.T
$bmp = New-Object System.Drawing.Bitmap $w, $hh
$g = [System.Drawing.Graphics]::FromImage($bmp)
$hdc = $g.GetHdc(); [WP]::PrintWindow($h, $hdc, 2) | Out-Null; $g.ReleaseHdc($hdc)
$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
"ok ${w}x${hh} at $($r.L),$($r.T) -> $Out"
