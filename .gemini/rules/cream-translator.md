# CreamTranslator Antigravity System Rules

Bản quy tắc này được tải tự động bởi Google Antigravity (CLI & IDE) khi hoạt động trong workspace CreamTranslator.

## 1. Giao tiếp & Phản hồi
- Bắt buộc phản hồi bằng tiếng Việt.
- Mọi nội dung tiếng Việt (phản hồi trực tiếp, câu hỏi/tùy chọn trong `ask_question`, tài liệu Markdown, comment mã nguồn) **BẮT BUỘC sử dụng tiếng Việt có dấu đầy đủ**, tuyệt đối không viết tiếng Việt không dấu.
- Bắt đầu mọi phản hồi bằng:
  `Enhanced prompt: [A clearer, grammatically corrected version of prompt].`
- Tuyệt đối không dùng emoji hoặc ký tự trang trí.
- Báo cáo chính xác dựa trên log và kết quả thực tế, không suy đoán.

## 2. Quy trình & Bảo vệ Nhánh
- Tuân thủ quy trình 7 bước trong [.agents/rules/git-workflow.md](../../.agents/rules/git-workflow.md).
- Không bao giờ commit/push trực tiếp vào `main`.
- Luôn dùng subagent (`TypeName: "self"`, `Workspace: "share"`) để review PR, sửa các finding, kiểm tra lại CI và merge PR khi hoàn tất.

## 3. Kiến trúc & Kiểm thử
- **Bắt buộc đọc đầy đủ** các tệp rule trong `.agents/rules/` trước khi chốt kế hoạch hoặc bắt đầu chỉnh sửa mã nguồn.
- Tuân thủ ranh giới Layered Interface-First: [.agents/rules/architecture-invariants.md](../../.agents/rules/architecture-invariants.md).
- Tuân thủ bất biến Android 14+: [.agents/rules/android-invariants.md](../../.agents/rules/android-invariants.md).
- Tuân thủ quy chuẩn kiểm thử JVM & Robolectric: [.agents/rules/testing-standards.md](../../.agents/rules/testing-standards.md).
