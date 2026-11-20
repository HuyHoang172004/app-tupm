param([string]$Voice = 'Microsoft Zira Desktop')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$feature = Join-Path $repo 'feature/audio_guide/src/main/res'
[xml]$content = Get-Content -LiteralPath (Join-Path $feature 'values/audio_guide_content.xml') -Raw -Encoding UTF8
$output = Join-Path $feature 'raw'
New-Item -ItemType Directory -Path $output -Force | Out-Null
$speech = New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
    $speech.SelectVoice($Voice)
    $speech.Rate = 0
    $format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
    foreach ($name in @('town', 'river', 'food')) {
        $text = ($content.resources.string | Where-Object { $_.name -eq "audio_guide_${name}_body" }).InnerText
        if (-not $text) { throw "Missing original transcript for $name" }
        $path = Join-Path $output "audio_guide_$name.wav"
        $speech.SetOutputToWaveFile($path, $format)
        $speech.Speak($text)
        $speech.SetOutputToNull()
        Get-Item -LiteralPath $path | Select-Object Name, Length
    }
} finally { $speech.Dispose() }
