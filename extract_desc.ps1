$impl_dir = "C:\Users\ardas\IdeaProjects\Spellbreak\src\main\java\me\ratatamakata\spellbreak\abilities\impl"
$files = Get-ChildItem -Path $impl_dir -Filter "*.java"

$results = @()
foreach ($file in $files) {
    $content = Get-Content $file.FullName -Raw
    if ($content -match 'public String getDescription\(\)\s*\{\s*return\s*"(.*?)";\s*\}') {
        $desc = $matches[1]
        $results += "$($file.Name): $desc"
    }
}
$results | Out-File -FilePath "C:\Users\ardas\IdeaProjects\Spellbreak\desc_out.txt"
