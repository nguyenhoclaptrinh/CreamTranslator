# CreamTranslator Knowledge Base & Technical Documentation

Chào mừng bạn đến với trung tâm lưu trữ tri thức kỹ thuật và tài liệu kiến trúc của **CreamTranslator**. Thư mục này đóng vai trò là "Nguồn chân lý duy nhất" (Single Source of Truth) ghi nhận toàn bộ các quyết định kiến trúc nền tảng, mô hình đe dọa bảo mật, và các đề xuất tính năng tương lai.

---

## 1. Cấu trúc Phân tầng Tài liệu (Documentation Structure)

```
docs/
├── README.md               # Mục lục tri thức và hướng dẫn chung (tệp này)
├── FEATURE_MAP.md          # Bản đồ tính năng và ma trận đối sánh cạnh tranh
├── THREAT_MODEL.md         # Mô hình đe dọa bảo mật và cam kết Anti-Cheat
├── adr/                    # Architecture Decision Records (Quyết định kiến trúc đã chốt)
│   ├── ADR-001-on-device-mlkit-pipeline.md
│   ├── ADR-002-zero-accessibility-architecture.md
│   ├── ADR-003-inplace-inpainting-and-overlay.md
│   ├── ADR-004-local-first-monitoring-and-diagnostic-logging.md
│   ├── ADR-005-ai-agent-optimized-architecture.md
│   └── ADR-006-harness-engineering-and-ratchet-architecture.md
├── proposals/              # Requests for Comments / Proposals (Đề xuất tính năng tương lai)
│   └── RFC-001-floating-quick-action-menu.md
└── templates/              # Biểu mẫu tài liệu chuẩn hóa
    ├── TEMPLATE_ADR.md     # Biểu mẫu ghi nhận quyết định kiến trúc
    └── TEMPLATE_RFC.md     # Biểu mẫu đặc tả đề xuất tính năng mới
```

---

## 2. Bản đồ Tính năng & Đối sánh Năng lực Cạnh tranh (Feature Map & Benchmarking)

Xem chi tiết tại: **[FEATURE_MAP.md](file:///d:/Workspaces/Tools/CreamTranslator/docs/FEATURE_MAP.md)**

Tài liệu này cung cấp:
- Ma trận đối sánh năng lực kỹ thuật giữa CreamTranslator và 5 ứng dụng dịch màn hình phổ biến nhất trên Android (Tap To Translate, Ever Translator, Bubble Screen Translate, Kaku, Instant Translate).
- Phân tích ranh giới lợi thế cạnh tranh cốt lõi (Core Moats): 100% On-Device, miễn nhiễm Anti-Cheat hoàn toàn, thay thế văn bản trực tiếp inpainting, kiến trúc kiểm thử chuẩn công nghiệp.
- Bản đồ tính năng 4 tầng theo chu kỳ phát hành từ v1.0 đến v2.5+.

---

## 3. Danh mục Quyết định Kiến trúc (Architecture Decision Records - ADR)

Các bản ADR ghi nhận các quyết định công nghệ và ranh giới bất biến đã được áp dụng vào codebase:

| Mã ADR | Tiêu đề | Trạng thái | Tóm tắt kỹ thuật |
| :--- | :--- | :--- | :--- |
| **[ADR-001](file:///d:/Workspaces/Tools/CreamTranslator/docs/adr/ADR-001-on-device-mlkit-pipeline.md)** | Selection of On-Device ML Kit Over Cloud Translation APIs | `Accepted` | Sử dụng Google ML Kit On-Device (Text Recognition v2 & Translation) thay thế Cloud APIs để triệt tiêu chi phí định kỳ và bảo mật dữ liệu tuyệt đối. |
| **[ADR-002](file:///d:/Workspaces/Tools/CreamTranslator/docs/adr/ADR-002-zero-accessibility-architecture.md)** | Zero-Accessibility Architecture for Anti-Cheat Compliance | `Accepted` | Sử dụng MediaProjection và In-Place Translation Overlay độc lập; tuyệt đối không sử dụng AccessibilityService để tránh bị hệ thống Anti-Cheat của game cấm tài khoản. |
| **[ADR-003](file:///d:/Workspaces/Tools/CreamTranslator/docs/adr/ADR-003-inplace-inpainting-and-overlay.md)** | In-Place Inpainting and Overlay Rendering Architecture | `Accepted` | Render chữ đè trực tiếp lên màn hình tại đúng toạ độ bounding box với mẫu màu nền tương phản tự động (TextInpainter) và StaticLayout. |
| **[ADR-004](file:///d:/Workspaces/Tools/CreamTranslator/docs/adr/ADR-004-local-first-monitoring-and-diagnostic-logging.md)** | Local-First Monitoring, Diagnostic Logging, and Pipeline Tracing Architecture | `Accepted` | Xây dựng hệ thống giám sát và đệm log cục bộ 100%, bảo vệ dữ liệu nhạy cảm qua LogSanitizer, đệm vòng in-memory, xoay vòng file đĩa tối đa 2MB và đo đạc độ trễ Pipeline. |
| **[ADR-005](file:///d:/Workspaces/Tools/CreamTranslator/docs/adr/ADR-005-ai-agent-optimized-architecture.md)** | AI-Agent-Optimized Layered Architecture and Testing Harness | `Accepted` | Tái cấu trúc Layered Interface-First cho OverlayService, tích hợp Robolectric trên JVM và module hóa tài liệu hướng dẫn Agent. |
| **[ADR-006](file:///d:/Workspaces/Tools/CreamTranslator/docs/adr/ADR-006-harness-engineering-and-ratchet-architecture.md)** | Mechanical Harness Engineering, Architectural Ratchet, and Property-Based Testing Architecture | `Accepted` | Thắt chặt Detekt rules, thiết lập rào chắn kiến trúc tự động bằng Konsist, State Machine vòng đời chụp màn hình và Property-Based Fuzzing với Kotest. |

---

## 4. Danh mục Đề xuất Tính năng (Requests for Comments - RFC)

Các bản RFC đặc tả các ý tưởng, cải tiến trải nghiệm người dùng hoặc tính năng mới được thảo luận nhưng chưa triển khai ngay:

| Mã RFC | Tiêu đề | Trạng thái | Tóm tắt ý tưởng |
| :--- | :--- | :--- | :--- |
| **[RFC-001](file:///d:/Workspaces/Tools/CreamTranslator/docs/proposals/RFC-001-floating-quick-action-menu.md)** | Floating Quick Action Menu (Lớp đệm điều khiển nút nổi) | `Proposed` | Xây dựng menu nan quạt tròn (Radial/Arc Menu) bung ra khi nhấn giữ nút nổi 600ms, vừa đóng vai trò lớp đệm chống tắt nhầm, vừa tạo khe cắm mở rộng tính năng trong tương lai. |

---

## 5. Hướng dẫn Đóng góp Tài liệu (Contribution Guide)

Mọi lập trình viên và AI Agent khi hoạt động trong dự án đều phải tuân thủ nghiêm ngặt nguyên tắc:
1. **Khi phát sinh ý tưởng/tính năng mới chưa làm ngay**: Bắt buộc tạo bản RFC trong `docs/proposals/` theo mẫu [TEMPLATE_RFC.md](file:///d:/Workspaces/Tools/CreamTranslator/docs/templates/TEMPLATE_RFC.md).
2. **Khi thay đổi cấu trúc nền tảng, công nghệ hoặc bảo mật**: Bắt buộc tạo hoặc cập nhật bản ADR trong `docs/adr/` theo mẫu [TEMPLATE_ADR.md](file:///d:/Workspaces/Tools/CreamTranslator/docs/templates/TEMPLATE_ADR.md).
3. **Cập nhật mục lục**: Bổ sung dòng tóm tắt tương ứng vào bảng mục lục trong tệp `docs/README.md` này.
