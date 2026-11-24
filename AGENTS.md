# AGENTS.md - Cẩm nang hoạt động & Nguyên tắc dành cho AI Agent trong CreamTranslator

Tài liệu này là chỉ dẫn hệ thống bắt buộc dành cho mọi AI coding agent (Antigravity CLI/IDE, Cursor, Claude Code, OpenCode, Codex) hoạt động trên repository **CreamTranslator**.

---

## 1. Quick Reference Matrix (Ma trận Tra cứu Nhanh)

Để tối ưu hóa dung lượng token ngữ cảnh, các quy định kỹ thuật chi tiết được phân rã thành các tệp chuyên đề trong thư mục `.agents/rules/`:

| Lĩnh vực | Tệp Quy chuẩn Chuyên biệt | Tóm tắt Bất biến Cốt lõi |
| :--- | :--- | :--- |
| **Kiến trúc Mã nguồn** | [.agents/rules/architecture-invariants.md](file:///.agents/rules/architecture-invariants.md) | Layered Interface-First; Service là Thin Host; giải phóng Bitmap (`recycle()`); không lưu static Context. |
| **Bất biến Android 14+** | [.agents/rules/android-invariants.md](file:///.agents/rules/android-invariants.md) | Đăng ký `MediaProjection.Callback` TRƯỚC KHI gọi `createVirtualDisplay()`; ForegroundService notification. |
| **Kiểm thử Tự động** | [.agents/rules/testing-standards.md](file:///.agents/rules/testing-standards.md) | Không gọi Android framework tĩnh trong JVM test; sử dụng Robolectric và Fakes; kiểm thử luồng bằng Turbine. |
| **Quy trình Git & PR** | [.agents/rules/git-workflow.md](file:///.agents/rules/git-workflow.md) | Bảo vệ nhánh `main`; Conventional Commits; đồng bộ nhánh trước khi mở PR; review, fix và merge qua subagent (hoặc dừng chờ nếu chưa có lệnh merge). |

---

## 2. Quy tắc Giao tiếp & Ngôn ngữ Phản hồi

- **Ngôn ngữ phản hồi mặc định**: Agent **BẮT BUỘC phản hồi bằng tiếng Việt** trong toàn bộ quá trình trao đổi, cập nhật trạng thái, phân tích lỗi, review code và tóm tắt kết quả.
- **Tiêu đề chuẩn hóa bắt buộc**: Mọi phản hồi gửi tới người dùng đều phải bắt đầu bằng dòng:
  ```text
  Enhanced prompt: [Phiên bản prompt tiếng Anh/Việt được viết lại rõ ràng, sửa lỗi chính tả và ngữ pháp nhưng giữ nguyên ý định gốc].
  ```
- **Bảo toàn thuật ngữ chuyên ngành chuẩn**: Giữ nguyên các thuật ngữ kỹ thuật phổ biến (`CI/CD`, `PR`, `commit`, `merge`, `branch`, `coroutine`, `bitmap`, `lifecycle`, `overlay`, `lint`, `spotless`, `detekt`, `unit test`).
- **Phong cách & Định dạng**: Trực tiếp, chính xác, súc tích. Tuyệt đối không dùng emoji hoặc ký tự trang trí. Báo cáo dựa trên kết quả quan sát thực tế và dữ liệu log.

---

## 3. Quy trình Làm việc Chuẩn 7 Bước (Workflow Cheatsheet)

```bash
# 1. Cập nhật nhánh main mới nhất và tạo feature branch
git checkout main
git pull origin main
git checkout -b <type>/<ten-tinh-nang-ngan-gon>

# 2. Thực hiện sửa đổi mã nguồn và kiểm tra cục bộ
# ... Chỉnh sửa code ...

# 3. Quản trị tri thức: Nếu phát sinh ý tưởng mới chưa làm -> tạo RFC; nếu đổi kiến trúc -> tạo/cập nhật ADR
# (Tuân thủ theo mẫu trong docs/templates/ và cập nhật vào docs/README.md)

# 4. Format mã nguồn tự động
./gradlew spotlessApply || true

# 5. Commit bằng Conventional Commits
git add .
git commit -m "<type>(<pham-vi>): <mo-ta-ngan-gon>"

# 6. Đồng bộ nhánh với main mới nhất trước khi mở PR
git fetch origin main
git merge origin/main

# 7. Đẩy nhánh lên remote và mở Pull Request
git push -u origin <type>/<ten-tinh-nang-ngan-gon>
gh pr create --title "<type>(<pham-vi>): <tieu-de>" --body "<Mo-ta-chi-tiet-thay-doi>"

# 8. Giám sát các CI checks trên PR cho tới khi toàn bộ xanh
gh pr checks <pr-number> --watch

# 9. Giao phó subagent (TypeName: "self", Workspace: "share") review, fix finding, xác minh CI và merge PR
# (Hoặc dừng lại báo cáo nếu chưa có lệnh merge / người dùng yêu cầu review thủ công)
```

---

## 4. Quản lý Phiên bản Ngữ nghĩa (Semantic Versioning)

- Nguồn chân lý duy nhất định nghĩa trong [gradle.properties](file:///gradle.properties) (`appVersionMajor`, `appVersionMinor`, `appVersionPatch`).
- Kiểm tra phiên bản hiện tại từ dòng lệnh:
  ```bash
  ./gradlew -q printAppVersion
  ```

---

## 5. Quy trình CI/CD & Fast-Pass

- Kích hoạt khi có `pull_request` vào `main`.
- Các PR chỉ chỉnh sửa tài liệu (`*.md`, `docs/**`, `.agents/**`) sẽ tự động được gán `fast-pass` (~2 giây).
- Code PR chạy 2 jobs song song trên RAM Disk (`tmpfs` 2GB):
  1. `Code Quality & Unit Tests`: Spotless, Detekt, LintDebug, TestDebugUnitTest.
  2. `Build Assemble Debug APK`: Biên dịch APK Debug.
- Release chính thức chỉ được tạo ra khi có Git Tag dạng `v*.*.*` hoặc kích hoạt thủ công.
