#!/usr/bin/env bash
# Script xác thực siêu tốc cho AI Agent và lập trình viên trước khi commit
# Chạy Spotless, Detekt và Unit Tests trên JVM mà không build full APK

set -e

echo "=== Bắt đầu vòng lặp kiểm tra nhanh (Fast-Check) ==="

echo "1. Kiểm tra định dạng mã nguồn (Spotless)..."
./gradlew spotlessCheck

echo "2. Phân tích tĩnh mã nguồn (Detekt)..."
./gradlew detekt

echo "3. Chạy Unit Tests trên JVM (TestDebugUnitTest)..."
./gradlew testDebugUnitTest

echo "=== Toàn bộ kiểm tra cục bộ đạt chuẩn chất lượng! ==="
