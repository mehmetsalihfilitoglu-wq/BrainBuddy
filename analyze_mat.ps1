# Analyze MAT LGS JSON: parse failures, short_item (exact + heuristic)
$ErrorActionPreference = "Stop"
$MAT_DIR = Join-Path $PSScriptRoot "app\src\main\assets\lgs_import\mat"

$PROBLEM_KEYWORDS = @("problemi","problem","oran","yüzde","grafik","tablo","şekilde","aşağıdaki","metne göre","parçaya göre")

function Get-Stem($q) {
    $s = if ($q.stem) { $q.stem } elseif ($q.questionText) { $q.questionText } else { "" }
    return ($s + "").Trim()
}

function Parse-LgsQuestion($o, $idx) {
    $stem = (Get-Stem $o)
    if ([string]::IsNullOrWhiteSpace($stem)) { return @{fail=$true; reason="stem_blank"} }
    
    $optsRaw = $o.options; if (-not $optsRaw) { $optsRaw = $o.choices }
    if ($null -eq $optsRaw) { return @{fail=$true; reason="options_missing"} }
    
    $arr = @(); if ($optsRaw -is [array]) { $arr = $optsRaw } else { $arr = @($optsRaw) }
    $rawOpts = @($arr | ForEach-Object { if ($_ -ne $null) { $_.ToString().Trim() } else { "" } } | Where-Object { $_ -ne "" })
    if ($rawOpts.Count -lt 2) { return @{fail=$true; reason="fewer_than_2_options_after_filter ($($rawOpts.Count) valid)"} }
    
    return @{fail=$false; stem=$stem; questionType=($o.questionType -replace "^\s+|\s+$","")}
}

function Has-ProblemKeyword($stem) {
    $s = $stem.ToLowerInvariant()
    foreach ($kw in $PROBLEM_KEYWORDS) { if ($s -match [regex]::Escape($kw)) { return $true } }
    return $false
}

function Is-ShortItemHeuristic($stem, $qt) {
    $len = $stem.Length
    if ($qt -eq "short_item") { return $true }
    if ($len -lt 80) { return $true }
    if ($len -lt 120 -and -not (Has-ProblemKeyword $stem)) { return $true }
    return $false
}

$parseFailures = @()
$shortItemExact = @()
$shortItemHeuristic = @()

$files = Get-ChildItem -Path $MAT_DIR -Filter "*.json" | Sort-Object Name
foreach ($f in $files) {
    $rel = "app/src/main/assets/lgs_import/mat/$($f.Name)"
    try {
        $json = Get-Content -Path $f.FullName -Raw -Encoding UTF8
        $data = $json | ConvertFrom-Json
    } catch {
        $parseFailures += @{file=$rel; index=-1; stem_preview=""; reason="file_load_error: $($_.Exception.Message)"}
        continue
    }
    $questions = @(); if ($data.questions) { $questions = @($data.questions) }
    for ($i = 0; $i -lt $questions.Count; $i++) {
        $q = $questions[$i]
        $r = Parse-LgsQuestion $q $i
        if ($r.fail) {
            $stemPrev = (Get-Stem $q)
            if (-not $stemPrev) { $alt = if ($q.question) { $q.question } else { "" }; $stemPrev = if ($alt) { "(stem blank; 'question' has text: $($alt.Substring(0,[Math]::Min(60,$alt.Length)))...)" } else { "(no stem)" } }
            $parseFailures += @{file=$rel; index=$i; stem_preview=$stemPrev.Substring(0, [Math]::Min(80, $stemPrev.Length)); reason=$r.reason}
        } else {
            $fullQ = $q | ConvertTo-Json -Compress
            if ($r.questionType -eq "short_item") { $shortItemExact += @{file=$rel; index=$i; q=$q; full=$fullQ} }
            if (Is-ShortItemHeuristic $r.stem $r.questionType) {
                $shortItemHeuristic += @{file=$rel; index=$i; stem=$r.stem; stem_length=$r.stem.Length; questionType=$r.questionType; full_question=$q}
            }
        }
    }
}

Write-Host ("="*60)
Write-Host "1. PARSE-FAILING QUESTIONS"
Write-Host ("="*60)
foreach ($p in $parseFailures) {
    Write-Host "  File: $($p.file)"
    Write-Host "  Index: $($p.index)"
    Write-Host "  Stem preview: $($p.stem_preview)"
    Write-Host "  Reason: $($p.reason)"
    Write-Host ""
}

Write-Host ("="*60)
Write-Host "2. SHORT_ITEM (questionType == 'short_item')"
Write-Host ("="*60)
foreach ($s in $shortItemExact) {
    $stem = Get-Stem $s.q
    Write-Host "  File: $($s.file), Index: $($s.index)"
    Write-Host "  Stem: $(if($stem.Length -gt 100){$stem.Substring(0,100)+'...'}else{$stem})"
    Write-Host "  Full: $($s.full)"
    Write-Host ""
}

Write-Host ("="*60)
Write-Host "3. SHORT_ITEM (heuristic)"
Write-Host ("="*60)
foreach ($s in $shortItemHeuristic) {
    Write-Host "  File: $($s.file), Index: $($s.index)"
    Write-Host "  Stem length: $($s.stem_length), questionType: $($s.questionType)"
    Write-Host "  Stem: $(if($s.stem.Length -gt 100){$s.stem.Substring(0,100)+'...'}else{$s.stem})"
    Write-Host ""
}

$out = @{
    parse_failures = $parseFailures
    short_item_exact = $shortItemExact | ForEach-Object { @{file=$_.file; index=$_.index; stem=(Get-Stem $_.q); full_question=$_.q} }
    short_item_heuristic = $shortItemHeuristic
}
$outPath = Join-Path $PSScriptRoot "mat_analysis_output.json"
$out | ConvertTo-Json -Depth 10 | Set-Content -Path $outPath -Encoding UTF8
Write-Host "Output saved to: $outPath"
Write-Host "Summary: $($parseFailures.Count) parse failures, $($shortItemExact.Count) short_item exact, $($shortItemHeuristic.Count) short_item heuristic"
