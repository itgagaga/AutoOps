# 将本目录初始化为独立 Git 仓库，供智维平台本地修复（git diff 审计）使用。
# 父仓库 intelligent-ops-tools 不跟踪此处的 .git，避免嵌套仓库无法提交。
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

if (Test-Path .git) {
    Write-Host "已存在 .git，跳过初始化。"
    git status -sb
    exit 0
}

git init
git add .gitignore pom.xml scripts src
git commit -m "测试工程初始化"
Write-Host "已完成本地仓库初始化，可将本目录作为 Codex / Maven 工作区。"
