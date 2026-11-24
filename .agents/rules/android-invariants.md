# Android Platform Invariants (API 34+ / Android 14+)

Tài liệu này tổng hợp các ràng buộc bất biến của nền tảng Android mà mọi AI Agent bắt buộc phải tuân thủ nghiêm ngặt để tránh crash hệ thống hoặc vi phạm bảo mật.

---

## 1. Bất biến MediaProjection trên Android 14+ (API 34+)

1. **Thứ tự đăng ký Callback**:
   - Khi khởi tạo `MediaProjection`, **BẮT BUỘC phải gọi `mediaProjection.registerCallback(...)` TRƯỚC KHI gọi `mediaProjection.createVirtualDisplay(...)`**.
   - Vi phạm quy tắc này sẽ dẫn tới `SecurityException` hoặc crash tức thì trên các thiết bị Android 14+.

2. **Xử lý sự kiện dừng MediaProjection**:
   - Trong `MediaProjection.Callback.onStop()`, phải giải phóng toàn bộ tài nguyên liên quan (`VirtualDisplay.release()`, `ImageReader.close()`).

3. **Cấu hình Foreground Service**:
   - Service thực hiện chụp màn hình (`ScreenCaptureService`) bắt buộc phải khai báo thuộc tính:
     ```xml
     android:foregroundServiceType="mediaProjection"
     ```
   - Service phải được khởi chạy bằng `startForegroundService` kèm Notification hợp lệ có độ ưu tiên phù hợp trước khi lấy MediaProjection token.

---

## 2. Bất biến WindowManager & System Overlay

1. **Quyền Vẽ đè (SYSTEM_ALERT_WINDOW)**:
   - Trước khi gắn bất kỳ View nào vào `WindowManager`, bắt buộc phải kiểm tra `Settings.canDrawOverlays(context)`.
   - Layout parameters phải sử dụng:
     ```kotlin
     WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
     ```

2. **Xử lý cử chỉ & Touch Invariants**:
   - Các View lớp phủ hiển thị bản dịch (`InPlaceTranslationOverlay`) phải thiết lập `isClickable = true` và tiêu thụ sự kiện `ACTION_DOWN` để tránh xung đột cử chỉ với hệ thống và ứng dụng bên dưới.
