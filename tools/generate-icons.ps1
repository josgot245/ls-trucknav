# Erzeugt aus appicon.jpeg alle Launcher-Icons (adaptive + legacy) fuer LS TruckNav.
# Aufruf: powershell -ExecutionPolicy Bypass -File tools\generate-icons.ps1

param(
    [string]$Source = (Join-Path $PSScriptRoot '..\appicon.jpeg'),
    [string]$Res    = (Join-Path $PSScriptRoot '..\app\src\main\res')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$srcPath = (Resolve-Path $Source).Path
$src = [System.Drawing.Bitmap]::FromFile($srcPath)
Write-Host "Quelle: $srcPath ($($src.Width)x$($src.Height))"

# --- 1. Logo quadratisch ausschneiden -------------------------------------
# Motiv-Bounding-Box in appicon.jpeg: x 693..1058, y 140..501
# -> quadratischer Ausschnitt mit Rand, Mittellinie des Motivs (875.5 / 320.5)
$cropRect = New-Object System.Drawing.Rectangle 676, 121, 400, 400
$crop = $src.Clone($cropRect, $src.PixelFormat)

$logoSize = 400
$logo = New-Object System.Drawing.Bitmap $logoSize, $logoSize, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($logo)
$g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
$g.DrawImage($crop, (New-Object System.Drawing.Rectangle 0, 0, $logoSize, $logoSize))
$g.Dispose()
$crop.Dispose()
$src.Dispose()

# --- 2. JPEG-Rauschen / hellgrauen Schleier zu reinem Weiss normalisieren --
$white = [System.Drawing.Color]::White
for ($y = 0; $y -lt $logoSize; $y++) {
    for ($x = 0; $x -lt $logoSize; $x++) {
        $p = $logo.GetPixel($x, $y)
        if ($p.R -ge 232 -and [math]::Abs($p.R - $p.G) -le 8 -and [math]::Abs($p.G - $p.B) -le 8) {
            $logo.SetPixel($x, $y, $white)
        }
    }
}

# --- 3. Hilfsfunktionen ---------------------------------------------------
function New-Canvas([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $gfx = [System.Drawing.Graphics]::FromImage($bmp)
    $gfx.SmoothingMode     = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $gfx.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $gfx.PixelOffsetMode   = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $gfx.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    return @($bmp, $gfx)
}

function Get-RoundRect([single]$x, [single]$y, [single]$w, [single]$h, [single]$r) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = 2 * $r
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return $path
}

function Draw-Logo($gfx, [double]$canvas, [double]$fraction) {
    $w = [int][math]::Round($canvas * $fraction)
    $o = [int][math]::Round(($canvas - $w) / 2.0)
    $gfx.DrawImage($logo, (New-Object System.Drawing.Rectangle $o, $o, $w, $w))
}

function Save-Png($bmp, [string]$dir, [string]$name) {
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $out = Join-Path $dir $name
    $bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Host ("  {0,-42} {1,4}x{1}" -f $name, $bmp.Width)
}

# --- 4. Icons rendern -----------------------------------------------------
$borderColor = [System.Drawing.Color]::FromArgb(227, 227, 227)
$densities = @(
    @{ dir = 'mipmap-mdpi';    legacy = 48;  adaptive = 108 },
    @{ dir = 'mipmap-hdpi';    legacy = 72;  adaptive = 162 },
    @{ dir = 'mipmap-xhdpi';   legacy = 96;  adaptive = 216 },
    @{ dir = 'mipmap-xxhdpi';  legacy = 144; adaptive = 324 },
    @{ dir = 'mipmap-xxxhdpi'; legacy = 192; adaptive = 432 }
)

foreach ($d in $densities) {
    $dir  = Join-Path $Res $d.dir
    $lsz  = [int]$d.legacy
    $asz  = [int]$d.adaptive
    Write-Host $d.dir

    # 4a. Adaptive Icon: weisser Vollflaechen-Vordergrund (keine sichtbare Kante
    #     zwischen Motiv-Patch und Hintergrund), Motiv in der Safe-Zone
    $r = New-Canvas $asz
    $bmp = $r[0]; $gfx = $r[1]
    $gfx.Clear([System.Drawing.Color]::White)
    Draw-Logo $gfx $asz 0.56
    $gfx.Dispose()
    Save-Png $bmp $dir 'ic_launcher_foreground.png'
    $bmp.Dispose()

    # 4b. Legacy Icon: weisse Kachel, abgerundet, mit feiner Kontur
    $r = New-Canvas $lsz
    $bmp = $r[0]; $gfx = $r[1]
    $radius = [single]($lsz * 0.22)
    $path = Get-RoundRect 0 0 $lsz $lsz $radius
    $gfx.FillPath([System.Drawing.Brushes]::White, $path)
    $penW = [single]([math]::Max(1.0, $lsz / 96.0))
    $pen = New-Object System.Drawing.Pen $borderColor, $penW
    $gfx.DrawPath($pen, $path)
    $pen.Dispose(); $path.Dispose()
    Draw-Logo $gfx $lsz 0.74
    $gfx.Dispose()
    Save-Png $bmp $dir 'ic_launcher.png'
    $bmp.Dispose()

    # 4c. Legacy rundes Icon
    $r = New-Canvas $lsz
    $bmp = $r[0]; $gfx = $r[1]
    $inset = [single]([math]::Max(0.5, $lsz / 96.0))
    $gfx.FillEllipse([System.Drawing.Brushes]::White, $inset, $inset, ($lsz - 2 * $inset), ($lsz - 2 * $inset))
    $pen = New-Object System.Drawing.Pen $borderColor, ([single]([math]::Max(1.0, $lsz / 96.0)))
    $gfx.DrawEllipse($pen, $inset, $inset, ($lsz - 2 * $inset), ($lsz - 2 * $inset))
    $pen.Dispose()
    Draw-Logo $gfx $lsz 0.62
    $gfx.Dispose()
    Save-Png $bmp $dir 'ic_launcher_round.png'
    $bmp.Dispose()
}

# --- 5. 512px-Referenzicon (Doku / Store) ---------------------------------
$r = New-Canvas 512
$bmp = $r[0]; $gfx = $r[1]
$path = Get-RoundRect 0 0 512 512 112
$gfx.FillPath([System.Drawing.Brushes]::White, $path)
$gfx.DrawPath((New-Object System.Drawing.Pen $borderColor, 2), $path)
$path.Dispose()
Draw-Logo $gfx 512 0.74
$gfx.Dispose()
$ref = Join-Path $PSScriptRoot '..\appicon-512.png'
$bmp.Save($ref, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Host "Referenzicon: $ref"

$logo.Dispose()
Write-Host 'Fertig.'
