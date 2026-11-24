# Testing Standards & Verification Guidelines

Tài liệu này quy chuẩn hóa phương pháp viết kiểm thử tự động, sử dụng Robolectric và Fakes dành cho AI Agent trong **CreamTranslator**.

---

## 1. Nguyên tắc Kiểm thử JVM Thuần túy (Pure JVM Tests)

1. **Tuyệt đối không gọi Unmocked Android Framework Methods**:
   - Trong các unit test JVM không chạy qua Robolectric, không được gọi các method tĩnh của Android (ví dụ `android.graphics.Color.rgb()` hoặc `Color.argb()`).
   - Bắt buộc sử dụng phép toán dịch bit chuẩn:
     ```kotlin
     val alpha = (color shr 24) and 0xFF
     val red = (color shr 16) and 0xFF
     val green = (color shr 8) and 0xFF
     val blue = color and 0xFF
     ```

2. **Kiểm thử Coroutine & StateFlow**:
   - Sử dụng `runTest` từ `kotlinx.coroutines.test`.
   - Sử dụng thư viện `Turbine` để kiểm tra các luồng phát xạ của `StateFlow` hoặc `SharedFlow`:
     ```kotlin
     viewModel.state.test {
         assertEquals(ExpectedState, awaitItem())
         cancelAndIgnoreRemainingEvents()
     }
     ```

---

## 2. Tiêu chuẩn Kiểm thử với Robolectric

1. **Phạm vi áp dụng**:
   - Dành cho các lớp có tương tác với Android Context, Service Lifecycle, WindowManager, Views và LayoutParams.
   - Giúp AI Agent kiểm tra logic gắn/gỡ View và phản hồi sự kiện hệ thống mà không cần thiết bị thật.

2. **Kế thừa BaseRobolectricTest**:
   - Tất cả các test sử dụng Robolectric nên kế thừa từ `BaseRobolectricTest` để cấu hình đồng nhất `TestCoroutineDispatcher` và `ApplicationProvider`.

3. **Sử dụng Test Doubles (Fakes)**:
   - Ưu tiên sử dụng `FakeOcrEngine`, `FakeTranslationEngine`, `FakeScreenCapturer` thay vì MockK phức tạp khi kiểm thử luồng điều phối nghiệp vụ (`TranslationPipelineCoordinator`).
