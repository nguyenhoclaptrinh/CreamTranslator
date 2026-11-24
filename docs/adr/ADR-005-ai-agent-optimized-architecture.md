# ADR-005: AI-Agent-Optimized Layered Architecture and Testing Harness

## Status
Accepted

## Context
1. **Thiếu khả năng kiểm thử Android SDK trên JVM**: Dự án trước đây chủ yếu dựa vào các JVM test đơn giản cho các hàm thuần Kotlin (`TextInpainter`, `DefaultTextFilter`). Các thành phần cốt lõi tương tác với Android Framework (`OverlayService`, `WindowManager`, `View`) không thể kiểm thử tự động trên JVM nếu không có thiết bị thật hoặc emulator, cản trở AI Agent tự viết test và kiểm chứng cục bộ.
2. **Khối lượng mã tập trung quá mức (God Object)**: `OverlayService` (637 dòng) chịu trách nhiệm đa nhiệm: quản lý WindowManager, xử lý animation cử chỉ kéo thả, lắng nghe sự kiện hệ thống (Screen off), và điều phối luồng dịch. Việc này làm tăng nguy cơ phát sinh lỗi hồi quy (regressions) khi AI Agent thực hiện thay đổi.
3. **Mục tiêu tối ưu hóa cho AI Agent Coding**: Cần một kiến trúc phân tầng rõ ràng (Layered Interface-First), cô lập logic điều phối thành các Controller/Coordinator độc lập, tích hợp Robolectric để chạy test Android SDK trực tiếp trên JVM trong vài giây, và module hóa các quy tắc context để tiết kiệm token ngữ cảnh.

## Decision
1. **Layered Interface-First Architecture**:
   - Duy trì Single-Module `:app` để đảm bảo thời gian biên dịch Gradle và CI siêu tốc (< 1.5 phút).
   - Tách `OverlayService` thành một Service host mỏng (< 150 dòng), ủy quyền toàn bộ trách nhiệm sang 3 thành phần độc lập:
     - `TranslationPipelineCoordinator` (cùng interface `ITranslationCoordinator`): Đóng gói toàn bộ luồng điều phối OCR -> Filter -> Translate -> Inpaint.
     - `OverlayWindowManager` (cùng interface `IOverlayWindowManager`): Đóng gói toàn bộ logic đính kèm, cập nhật và gỡ bỏ view trên `WindowManager`.
     - `FloatingTriggerController`: Đóng gói logic bong bóng nổi, cử chỉ chạm/kéo và animation hít mép màn hình.
2. **Tích hợp Robolectric & Test Doubles**:
   - Bổ sung Robolectric vào cấu hình kiểm thử `testImplementation` trong Gradle.
   - Cung cấp `BaseRobolectricTest` và các Test Doubles chuẩn (`FakeOcrEngine`, `FakeTranslationEngine`, `FakeScreenCapturer`) giúp AI Agent dễ dàng dựng các bài kiểm thử giả lập nhanh chóng mà không phụ thuộc vào native libraries.
3. **Module hóa Context Chỉ dẫn AI Agent**:
   - Phân rã `AGENTS.md` thành các tài liệu chuyên đề tại `.agents/rules/` (`architecture-invariants.md`, `android-invariants.md`, `testing-standards.md`, `git-workflow.md`).
   - Cung cấp cấu hình tích hợp Antigravity Customizations tại `.gemini/rules/`.

## Consequences
### Positive (Lợi ích mang lại)
- **Tốc độ xác minh siêu nhanh**: AI Agent có thể chạy toàn bộ bộ kiểm thử gồm cả Android Lifecycle, View, WindowManager trong chưa đầy 15 giây trên JVM.
- **Cô lập lỗi hồi quy**: Thay đổi trong logic dịch không ảnh hưởng tới logic hiển thị hoặc touch gesture và ngược lại.
- **Tiết kiệm Token & Nâng cao Độ chính xác**: Agent chỉ cần đọc các file rule liên quan trực tiếp đến nhiệm vụ được giao.
- **Zero Technical Debt**: Không thêm thư viện DI phức tạp hay làm phân mảnh module Gradle không cần thiết.

### Negative / Trade-offs (Đánh đổi & Hạn chế)
- Thêm dependency Robolectric vào `testImplementation` làm tăng nhẹ dung lượng cache gradle dependencies ban đầu (~50MB).
- Đòi hỏi tính nhất quán khi khởi tạo coordinator và inject qua constructor trong `OverlayService`.

## References
- [AGENTS.md](file:///C:/Users/Nguyen/.gemini/antigravity/worktrees/CreamTranslator/refactor_ai_agent_coding/AGENTS.md)
- [.agents/rules/architecture-invariants.md](file:///C:/Users/Nguyen/.gemini/antigravity/worktrees/CreamTranslator/refactor_ai_agent_coding/.agents/rules/architecture-invariants.md)
- [.agents/rules/testing-standards.md](file:///C:/Users/Nguyen/.gemini/antigravity/worktrees/CreamTranslator/refactor_ai_agent_coding/.agents/rules/testing-standards.md)
- [ADR-003: In-Place Inpainting and Overlay Rendering Architecture](file:///C:/Users/Nguyen/.gemini/antigravity/worktrees/CreamTranslator/refactor_ai_agent_coding/docs/adr/ADR-003-inplace-inpainting-and-overlay.md)
