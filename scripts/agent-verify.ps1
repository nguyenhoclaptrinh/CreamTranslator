# Script xác thực siêu tốc cho AI Agent và lập trình viên trước khi commit
# Chạy Spotless, Detekt và Unit Tests trên JVM mà không build full APK

$ErrorActionPreference = "Stop"

Write-Host "=== Bắt đầu vòng lặp kiểm tra nhanh (Fast-Check) ===" -ForegroundColor Cyan

Write-Host "1. Kiểm tra định dạng mã nguồn (Spotless)..." -ForegroundColor Yellow
./gradlew spotlessCheck
if ($LASTEXITCODE -ne 0) {
    Write-Host "Lỗi định dạng mã nguồn! Hãy chạy './gradlew spotlessApply' để tự động sửa." -ForegroundColor Red
    exit 1
}

Write-Host "2. Phân tích tĩnh mã nguồn (Detekt)..." -ForegroundColor Yellow
./gradlew detekt
if ($LASTEXITCODE -ne 0) {
    Write-Host "Lỗi Detekt static analysis!" -ForegroundColor Red
    exit 1
}

Write-Host "3. Chạy Unit Tests trên JVM (TestDebugUnitTest)..." -ForegroundColor Yellow
./gradlew testDebugUnitTest
if ($LASTEXITCODE -ne 0) {
    Write-Host "Unit Test thất bại!" -ForegroundColor Red
    exit 1
}

Write-Host "=== Toàn bộ kiểm tra cục bộ đạt chuẩn chất lượng! ===" -ForegroundColor Green
