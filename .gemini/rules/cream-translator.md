# CreamTranslator Antigravity System Rules

Bản quy tắc này được tải tự động bởi Google Antigravity (CLI & IDE) khi hoạt động trong workspace CreamTranslator.

## 1. Giao tiếp & Phản hồi
- Bắt buộc phản hồi bằng tiếng Việt.
- Bắt đầu mọi phản hồi bằng:
  `Enhanced prompt: [A clearer, grammatically corrected version of prompt].`
- Tuyệt đối không dùng emoji hoặc ký tự trang trí.
- Báo cáo chính xác dựa trên log và kết quả thực tế, không suy đoán.

## 2. Quy trình & Bảo vệ Nhánh
- Tuân thủ quy trình 7 bước trong [.agents/rules/git-workflow.md](file:///C:/Users/Nguyen/.gemini/antigravity/worktrees/CreamTranslator/refactor_ai_agent_coding/.agents/rules/git-workflow.md).
- Không bao giờ commit/push trực tiếp vào `main`.
- Khi tạo PR và CI xanh: Dừng lại và báo cáo, tuyệt đối không tự ý merge khi chưa có lệnh tường minh từ người dùng.

## 3. Kiến trúc & Kiểm thử
- Tuân thủ ranh giới Layered Interface-First: [.agents/rules/architecture-invariants.md](file:///C:/Users/Nguyen/.gemini/antigravity/worktrees/CreamTranslator/refactor_ai_agent_coding/.agents/rules/architecture-invariants.md).
- Tuân thủ bất biến Android 14+: [.agents/rules/android-invariants.md](file:///C:/Users/Nguyen/.gemini/antigravity/worktrees/CreamTranslator/refactor_ai_agent_coding/.agents/rules/android-invariants.md).
- Tuân thủ quy chuẩn kiểm thử JVM & Robolectric: [.agents/rules/testing-standards.md](file:///C:/Users/Nguyen/.gemini/antigravity/worktrees/CreamTranslator/refactor_ai_agent_coding/.agents/rules/testing-standards.md).
