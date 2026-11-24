# ADR-006: Mechanical Harness Engineering, Architectural Ratchet, and Property-Based Testing Architecture

* **Trạng thái**: `Accepted`
* **Người đề xuất**: Antigravity AI Agent & Engineering Team
* **Ngày quyết định**: 2026-11-24

---

## 1. Bối cảnh & Vấn đề (Context)

Khi phát triển ứng dụng với sự tham gia sâu của các AI Coding Agent (LLM), việc chỉ dựa vào tài liệu văn bản hướng dẫn (`AGENTS.md`) hoặc prompt tự nhiên bộc lộ điểm yếu cố hữu:
1. **Agentic Shortcuts (Lối tắt cẩu thả)**: Khi gặp lỗi biên dịch hoặc test hỏng, AI agent thường có xu hướng sử dụng các giải pháp chắp vá bề nổi (superficial patches) như bắt ngoại lệ chung `catch (e: Exception)` để nuốt lỗi, viết comment trì hoãn (`// TODO: fix later`), hoặc bỏ qua các điều kiện biên.
2. **Thiếu rào chắn kiến trúc cơ học (Lack of Architectural Ratchet)**: Các quy tắc kiến trúc (như Service là Thin Host, không dùng GlobalScope, package structure) trước đây chỉ được kiểm tra thủ công trong code review.
3. **Kiểm thử mẫu tĩnh (Static Example Blindspot)**: Unit test truyền thống chỉ kiểm tra các ca cố định, dễ bỏ sót các trường hợp biên của văn bản Unicode, ký tự rác hoặc số liệu khi chạy OCR thực tế.

Tham khảo mô hình **Inverted Trust Pyramid** và **Ratchet Mechanism** của Lauren Tan, dự án cần một hệ thống rào chắn cơ học xác định (Deterministic Mechanical Barriers) để ngăn chặn vĩnh viễn các lớp lỗi này.

---

## 2. Quyết định Kiến trúc (Decision)

Chúng tôi quyết định hiện thực hóa triết lý "Harness Engineering & Ratchet Mechanism" bản địa hóa cho nền tảng Kotlin/Android như sau:

### 2.1. Thắt chặt rào chắn phân tích tĩnh Detekt
- Kích hoạt bắt buộc `TooGenericExceptionCaught: active: true` trong `detekt.yml`. Mọi khối `try-catch` phải bắt chính xác loại ngoại lệ (như `IOException`, `SecurityException`, `IllegalStateException`) hoặc có annotation `@Suppress("TooGenericExceptionCaught")` đi kèm lý do kỹ thuật rõ ràng.
- Kích hoạt `ForbiddenComment: active: true` để cấm hoàn toàn các bình luận thoái thác của AI (`TODO:`, `FIXME:`, `STOPSHIP:`, `HACK:`, `TEMP:`).

### 2.2. Thiết lập Cơ chế Bánh cóc Kiến trúc bằng Konsist
- Tích hợp thư viện `com.lemonappdev:konsist` vào test suite JVM.
- Xây dựng `ArchitectureInvariantsTest` để biến mọi bất biến kiến trúc thành các bài kiểm thử tự động trên CI:
  - Bắt buộc các lớp `Service` phải nằm trong package `..service..`.
  - Bắt buộc các lớp `Repository` phải nằm trong package `..data..` hoặc `..repository..`.
  - Bắt buộc các lớp `ViewModel` phải nằm trong package `..ui..` hoặc `..update..`.
  - Cấm hoàn toàn việc sử dụng `GlobalScope` trong toàn bộ mã nguồn production.

### 2.3. Đóng gói Bất biến Vòng đời bằng Type-Safe State Machine
- Refactor `ScreenCaptureManager`: Thay thế các cờ boolean rời rạc bằng `sealed interface CaptureLifecycleState` (`Uninitialized`, `CallbackRegistered`, `Active`, `Stopped`).
- Đảm bảo bất biến Android 14+: Callback luôn được đăng ký trước khi tạo Virtual Display, và trạng thái dừng được đồng bộ nguyên tử.

### 2.4. Tích hợp Kiểm thử Thuộc tính (Property-Based Fuzzing) với Kotest Property
- Bổ sung `io.kotest:kotest-property` vào JVM test suite.
- Xây dựng `TextFilterPropertyTest` chạy hàng nghìn chuỗi ngẫu nhiên (arbitrary unicode, numbers, whitespaces) để chứng minh tính bất biến toán học và khả năng miễn nhiễm với unhandled exceptions của pipeline xử lý chuỗi.

---

## 3. Hệ quả & Đánh đổi (Consequences)

### Tích cực (Pros):
- **Triệt tiêu mã nguồn rác (Zero AI Slop)**: AI agent không thể gian lận hay nuốt lỗi; mọi vi phạm đều làm gãy CI ngay lập tức.
- **Bảo toàn kiến trúc theo thời gian (The Ratchet only turns forward)**: Mã nguồn mới không thể vi phạm các quy tắc kiến trúc đã được đóng đinh bằng Konsist.
- **Độ tin cậy cao hơn**: Property-based testing phát hiện sớm các lỗi biên trước khi phát hành APK.

### Đánh đổi (Trade-offs & Maintenance):
- Thời gian chạy test suite JVM tăng thêm khoảng 3-5 giây do Konsist quét toàn bộ AST dự án và Kotest chạy hàng nghìn iterations.
- Yêu cầu developer và AI agent phải khai báo kiểu ngoại lệ chi tiết hơn khi viết mã nguồn mới.
