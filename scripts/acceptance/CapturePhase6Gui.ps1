param(
    [Parameter(Mandatory = $true)]
    [string]$Player,

    [Parameter(Mandatory = $true)]
    [string]$Output,

    [switch]$CloseFirst
)

Add-Type -AssemblyName System.Drawing
Add-Type @'
using System;
using System.Text;
using System.Runtime.InteropServices;

public static class Phase6WindowApi {
    public delegate bool EnumWindowsProc(IntPtr handle, IntPtr state);

    [StructLayout(LayoutKind.Sequential)]
    public struct Rect {
        public int Left;
        public int Top;
        public int Right;
        public int Bottom;
    }

    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumWindowsProc callback, IntPtr state);
    [DllImport("user32.dll")] public static extern int GetWindowText(IntPtr handle, StringBuilder text, int length);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr handle);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr handle, out uint processId);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr handle);
    [DllImport("user32.dll")] public static extern void keybd_event(byte key, byte scan, uint flags, UIntPtr extraInfo);
    [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extraInfo);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr handle, out Rect rect);
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr handle, IntPtr dc, uint flags);
}
'@

[Phase6WindowApi]::SetProcessDPIAware() | Out-Null
$target = [IntPtr]::Zero
[Phase6WindowApi]::EnumWindows({
    param($handle, $state)
    $title = [Text.StringBuilder]::new(256)
    [void][Phase6WindowApi]::GetWindowText($handle, $title, 256)
    # Window titles are localized (for example, Chinese does not contain
    # "Multiplayer"), so identify the Forge game window and then use the
    # launch username below to select the requested isolated client.
    if ([Phase6WindowApi]::IsWindowVisible($handle) -and $title.ToString() -match '^Minecraft\*? Forge ') {
        $processId = 0
        [void][Phase6WindowApi]::GetWindowThreadProcessId($handle, [ref]$processId)
        $commandLine = (Get-CimInstance Win32_Process -Filter "ProcessId=$processId").CommandLine
        if ($commandLine -match "--username $([regex]::Escape($Player))(?:\s|$)") {
            $script:target = $handle
        }
    }
    return $true
}, [IntPtr]::Zero) | Out-Null

if ($target -eq [IntPtr]::Zero) {
    throw "Minecraft window for player '$Player' was not found."
}

[Phase6WindowApi]::SetForegroundWindow($target) | Out-Null
Start-Sleep -Milliseconds 500
if ($CloseFirst) {
    [Phase6WindowApi]::keybd_event(0x1B, 0, 0, [UIntPtr]::Zero)
    [Phase6WindowApi]::keybd_event(0x1B, 0, 2, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 500
}
[Phase6WindowApi]::mouse_event(8, 0, 0, 0, [UIntPtr]::Zero)
[Phase6WindowApi]::mouse_event(16, 0, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Seconds 2

$rect = [Phase6WindowApi+Rect]::new()
[void][Phase6WindowApi]::GetWindowRect($target, [ref]$rect)
$bitmap = [Drawing.Bitmap]::new($rect.Right - $rect.Left, $rect.Bottom - $rect.Top)
$graphics = [Drawing.Graphics]::FromImage($bitmap)
$dc = $graphics.GetHdc()
try {
    [void][Phase6WindowApi]::PrintWindow($target, $dc, 2)
} finally {
    $graphics.ReleaseHdc($dc)
    $graphics.Dispose()
}
$outputPath = [IO.Path]::GetFullPath($Output)
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($outputPath)) | Out-Null
$bitmap.Save($outputPath, [Drawing.Imaging.ImageFormat]::Png)
$bitmap.Dispose()
Write-Output $outputPath
