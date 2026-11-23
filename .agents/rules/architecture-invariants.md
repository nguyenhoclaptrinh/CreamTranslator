# Architecture Invariants & Layered Interface-First Guidelines

Tài liệu này định nghĩa các nguyên tắc bất biến về mặt kiến trúc mã nguồn trong **CreamTranslator**, đảm bảo các AI Agent và lập trình viên không phá vỡ tính toàn vẹn hệ thống và không tạo nợ kỹ thuật.

---

## 1. Ranh giới Phân tầng (Layered Boundaries)

Codebase tuân thủ mô hình **Layered Interface-First** trong cấu trúc Single-Module `:app`:

1. **Engine Core (`com.cream.translator.core`)**:
   - Chứa logic xử lý thuật toán, OCR, dịch thuật và inpainting.
   - **Bất biến**: Không phụ thuộc ngược lại tầng `service` hoặc tầng `ui`.
   - Mọi thao tác tính toán (màu sắc, tọa độ chữ, độ tương phản) phải được trừu tượng hóa để có thể chạy độc lập trên JVM thuần túy.

2. **Service Layer (`com.cream.translator.service`)**:
   - Quản lý vòng đời Android Services (`ScreenCaptureService`, `OverlayService`) và điều phối trung tâm (`TranslatorController`).
   - **Bất biến**: Service chỉ đóng vai trò là một "Thin Host" (lớp vỏ mỏng quản lý vòng đời Android Service). Toàn bộ logic điều phối luồng dịch (OCR -> Filter -> Translate -> Inpaint) phải được ủy quyền cho `ITranslationCoordinator`.

3. **Overlay & UI Layer (`com.cream.translator.ui.overlay`)**:
   - Chịu trách nhiệm hiển thị giao diện nổi và vẽ chữ đè màn hình (`InPlaceTranslationOverlay`, `RegionSelectorView`).
   - Tương tác với hệ thống WindowManager phải được đóng gói qua `IOverlayWindowManager`.
   - Cử chỉ chạm, kéo và animation hít mép phải được cô lập trong `FloatingTriggerController`.

---

## 2. Quản lý Bộ nhớ & Phòng ngừa Memory Leak

1. **Giải phóng Bitmap triệt để**:
   - Luôn gọi `bitmap.recycle()` đối với mọi bitmap trung gian (frame capture từ MediaProjection, cropped regions, inpainting patches) ngay khi hoàn tất xử lý.
   - Tránh lưu giữ bitmap trong các biến thành viên dài hạn của Service hoặc View.

2. **Quản lý CoroutineScope**:
   - Mọi coroutine scope trong Service hoặc View phải gắn liền với vòng đời thực tế:
     - Trong Service: Hủy toàn bộ job (`scope.cancel()`) tại `onDestroy()`.
     - Trong View: Hủy coroutine tại `onDetachedFromWindow()`.
   - Luôn thiết lập `CoroutineExceptionHandler` có gắn thẻ nhận diện tên class để không nuốt ngoại lệ âm thầm.

3. **Bất biến về Context & Static References**:
   - Tuyệt đối không lưu trữ biến tĩnh (`companion object` hoặc `static`) tham chiếu tới `Context`, `Activity`, `Service` hoặc `View`.
   - Nếu cần sử dụng Application Context trong Singleton, chỉ lưu `context.applicationContext`.
