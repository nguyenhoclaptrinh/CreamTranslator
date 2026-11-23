# ADR-004: Local-First Monitoring, Diagnostic Logging, and Pipeline Tracing Architecture

## Status
Accepted

## Context
CreamTranslator hoạt động như một ứng dụng dịch màn hình on-device thời gian thực, chụp liên tục các khung hình bitmap đồ họa và trích xuất chữ qua OCR. Trong quá trình vận hành thực tế, ứng dụng đối mặt với các thách thức:
1. **Bảo mật và Quyền riêng tư (Zero Remote Exfiltration)**: Theo mô hình đe dọa [THREAT_MODEL.md](file:///d:/Workspaces/Tools/CreamTranslator/docs/THREAT_MODEL.md), CreamTranslator cam kết không bao giờ truyền bất kỳ dữ liệu hình ảnh, chuỗi OCR hay dữ liệu đo đạc (telemetry) nào ra bên ngoài mạng internet. Việc tích hợp các SDK giám sát đám mây (Firebase Crashlytics, Sentry, Datadog) sẽ phá vỡ bất biến bảo mật này và làm tăng kích thước APK thêm 3MB - 6MB.
2. **Khả năng chẩn đoán sự cố trên thiết bị thực tế**: Trên các bản Release, khi người dùng hoặc tester gặp sự cố (màn hình đen do DRM/FLAG_SECURE, lỗi tràn RAM bitmap, crash service nền), Logcat thông thường không thể truy xuất nếu không có máy tính cắm cáp ADB.
3. **Độ trễ và nghẽn hiệu năng trên Critical Path**: Pipeline xử lý gồm nhiều giai đoạn (Capture -> OCR -> Filtering -> Batch Translation -> Text Inpainting -> Overlay Render). Cần có cơ chế đo lường độ trễ từng bước độc lập mà không gây ảnh hưởng đến hiệu năng Main Thread hoặc làm chậm tốc độ hiển thị.

## Decision
Dự án quyết định thiết kế và triển khai kiến trúc **Local-First Monitoring & Diagnostic Logging** tự chủ hoàn toàn trong package `com.cream.translator.core.monitoring`:
- **Sanitized Logging Engine (`LogSanitizer` & `CreamLoggingTree`)**: Mọi câu log trước khi ghi đều được tự động làm sạch (sanitize/mask). Toàn bộ nội dung văn bản OCR thô, email, mã bảo mật, token hoặc thông tin nhạy cảm đều được thay thế bằng chuỗi độ dài an toàn (`[REDACTED_LEN_X]`), ngăn ngừa triệt để rò rỉ dữ liệu cá nhân vào log.
- **In-Memory Ring Buffer (`CircularLogBuffer`)**: Duy trì tối đa 1.000 log events gần nhất trong RAM với độ phức tạp $O(1)$ thread-safe, phục vụ việc hiển thị trực tiếp cho người dùng trong ứng dụng.
- **Asynchronous Rolling File Logger (`RotatingFileLogSink`)**: Ghi log ra đĩa bất đồng bộ thông qua Coroutines I/O trên phân vùng bảo mật `context.noBackupFilesDir.resolve("diagnostics")`. Giới hạn chặt chẽ 2 tệp xoay vòng (`active_session.log` và `previous_session.log`) với dung lượng tối đa 1MB/tệp (tổng 2MB), loại bỏ hoàn toàn nguy cơ phình to dung lượng bộ nhớ thiết bị.
- **Pipeline Latency Tracing (`PipelineTracer`)**: Đo lường thời gian xử lý từng chặng ở mức nano-giây, lưu giữ lịch sử metric và tính toán độ trễ trung bình nhằm hỗ trợ phát hiện điểm nghẽn hiệu năng trên các dòng máy cấu hình thấp.
- **Memory Pressure & Crash Diagnostics (`MemoryPressureMonitor` & `CrashDiagnosticCollector`)**: Tự động cảnh báo áp lực RAM khi hệ điều hành gửi tín hiệu `ComponentCallbacks2.onTrimMemory`, đồng thời cài đặt `Thread.UncaughtExceptionHandler` để lưu vết stacktrace và thông số phần cứng vào `crash_report.json` khi có ngoại lệ chưa xử lý.
- **User-Consented Diagnostic Export (`DiagnosticExportHelper` & `DiagnosticsCard`)**: Người dùng có thể kiểm tra trực tiếp nhật ký hệ thống trong màn hình Cài đặt, và chủ động xuất gói báo cáo chẩn đoán đính kèm file log qua Android Sharesheet (`FileProvider`) khi cần gửi phản hồi trên GitHub Issues.

## Consequences
### Positive (Lợi ích mang lại)
- **Bảo mật tuyệt đối**: Dữ liệu lưu 100% cục bộ; không có bất kỳ kết nối mạng hay SDK bên thứ ba nào can thiệp.
- **Kích thước siêu nhẹ**: Tận dụng thư viện Kotlin Coroutines và Timber có sẵn, mã nguồn bổ sung < 50KB, không làm tăng dung lượng APK.
- **Hiệu năng cao**: Cơ chế đệm vòng không chặn luồng chính (Main Thread); việc ghi đĩa được thực hiện bất đồng bộ.
- **Tiện dụng cho cộng đồng mã nguồn mở**: Người dùng có thể tự kiểm tra nội dung log và chủ động chia sẻ file chẩn đoán khi báo lỗi.

### Negative / Trade-offs (Đánh đổi & Hạn chế)
- **Không có báo cáo tự động từ xa**: Nhà phát triển không nhận được crash report tự động theo thời gian thực như Firebase/Sentry; việc thu thập thông tin phụ thuộc vào việc người dùng gửi file chẩn đoán khi mở GitHub Issue.
- **Dung lượng lưu trữ giới hạn**: Tối đa 2MB log và 1.000 events trong bộ nhớ, các log cũ hơn sẽ bị xoay vòng và ghi đè tự động.

## References (Tài liệu tham chiếu)
- Mô hình đe dọa: [docs/THREAT_MODEL.md](file:///d:/Workspaces/Tools/CreamTranslator/docs/THREAT_MODEL.md)
- Kế hoạch triển khai: [monitoring_logging_plan.md](file:///C:/Users/Nguyen/.gemini/antigravity/brain/56334846-611c-499c-8fd2-197a5e962a1d/monitoring_logging_plan.md)
