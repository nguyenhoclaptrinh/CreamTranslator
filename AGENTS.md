# AGENTS.md - Cẩm nang hoạt động & Nguyên tắc dành cho AI Agent trong CreamTranslator

Tài liệu này là chỉ dẫn hệ thống bắt buộc dành cho mọi AI coding agent (Antigravity, Cursor, Claude Code, OpenCode, Codex) hoạt động trên repository **CreamTranslator**.

---

## 1. Quy tắc giao tiếp & Ngôn ngữ phản hồi (Language & Communication Directives)

- **Ngôn ngữ phản hồi mặc định**: Agent **BẮT BUỘC phản hồi bằng tiếng Việt** trong toàn bộ quá trình trao đổi, cập nhật trạng thái, phân tích lỗi, review code và tóm tắt kết quả, trừ khi người dùng có yêu cầu bằng ngôn ngữ khác một cách rõ ràng.
- **Tiêu đề chuẩn hóa bắt buộc**: Mọi phản hồi gửi tới người dùng đều phải bắt đầu bằng dòng:
  ```text
  Enhanced prompt: [Phiên bản prompt tiếng Anh/Việt được viết lại rõ ràng, sửa lỗi chính tả và ngữ pháp nhưng giữ nguyên ý định gốc].
  ```
- **Bảo toàn thuật ngữ chuyên ngành chuẩn**: Giữ nguyên các thuật ngữ kỹ thuật phổ biến trong kỹ thuật phần mềm và hệ sinh thái Android, tránh dịch gượng ép gây khó hiểu (ví dụ: `CI/CD`, `PR`, `commit`, `merge`, `rebase`, `branch`, `build`, `coroutine`, `bitmap`, `lifecycle`, `foreground service`, `overlay`, `workflow`, `lint`, `spotless`, `detekt`, `unit test`, `memory leak`).
- **Phong cách giao tiếp**:
  - Trực tiếp, chính xác, súc tích và có tính hành động cao.
  - Tuyệt đối không dùng ngôn ngữ cảm thán thái quá, tâng bốc, xin lỗi rườm rà hay biểu tượng cảm xúc (emoji) trang trí.
  - Báo cáo dựa trên kết quả quan sát thực tế và dữ liệu log, không suy đoán vô căn cứ.

---

## 2. Nguyên tắc làm việc của Agent (Operational & Working Principles)

1. **Hiểu rõ bối cảnh & Phân tích nguyên nhân gốc rễ (Root Cause Analysis)**:
   - Trước khi sửa đổi bất kỳ tệp tin nào, phải đọc và kiểm tra bối cảnh xung quanh, cấu hình hiện tại và cấu trúc dự án.
   - Luôn giải quyết nguyên nhân gốc rễ của vấn đề thay vì can thiệp vá víu triệu chứng bề mặt.
   - Đối với các tác vụ phức tạp gồm nhiều bước (refactor, tối ưu CI/CD, thay đổi kiến trúc), phải lập kế hoạch rõ ràng (`/plan`) trước khi bắt đầu triển khai.
2. **Kiểm chứng dựa trên bằng chứng thực tế (Evidence-Based Verification)**:
   - Tuyệt đối không giả định một thao tác hay lệnh shell đã thành công nếu chưa có bằng chứng log/output chứng minh.
   - Luôn kiểm tra lại trạng thái sau mỗi bước chỉnh sửa (`git status`, `git diff`, kiểm tra lint, chạy test).
   - Rõ ràng phân định giữa phần việc đã kiểm chứng hoàn tất và phần việc chưa kiểm chứng.
3. **Tuân thủ quy trình Git & Chính sách bảo vệ nhánh (Branch Protection)**:
   - **Nhánh `main` được bảo vệ nghiêm ngặt** với `enforce_admins: true`.
   - **Tuyệt đối không push trực tiếp lên `main`**.
   - Mọi thay đổi đều phải được thực hiện trên feature/fix branch và đưa vào qua Pull Request (PR) nhắm vào `main`.
   - Mỗi commit phải tuân thủ chuẩn **Conventional Commits** (`feat:`, `fix:`, `perf:`, `docs:`, `refactor:`, `test:`, `ci:`).
   - **Bất biến về quyền Merge PR**:
     - Agent **CHỈ ĐƯỢC PHÉP** tạo PR, giám sát CI hoàn tất và báo cáo link PR kèm tóm tắt kỹ thuật cho người dùng.
     - **DỪNG LẠI TẠI ĐÂY** để người dùng trực tiếp review và đưa ra quyết định.
     - **TUYỆT ĐỐI KHÔNG TỰ Ý MERGE PR** trừ khi nhận được mệnh lệnh rõ ràng, tường minh từ người dùng (ví dụ: *"hãy merge PR"* hoặc *"CI xanh thì merge luôn"*).
4. **An toàn, phòng vệ và không phá hủy (Non-Destructive Operations)**:
   - Không chạy các lệnh xóa file, force-push hay xóa nhánh khi chưa xác định rõ rủi ro và tác động phụ.
   - Không che giấu lỗi bằng cách chèn `@Suppress`, bỏ qua exception im lặng hoặc tắt các bộ kiểm tra chất lượng mà không có lý do kỹ thuật chính đáng.
5. **Tiêu chuẩn đề xuất giải pháp & Chống nợ kỹ thuật (Anti-Technical Debt & Deep Evaluation)**:
   - **Tuyệt đối không đề xuất giải pháp vá víu, hời hợt**: Không chọn phương án chỉ để "chữa cháy tạm thời", tiết kiệm vài giây mà để lại nợ kỹ thuật, code rác hoặc phá vỡ tính nhất quán của kiến trúc.
   - **Bắt buộc đánh giá và so sánh đa chiều**: Khi đưa ra các giải pháp giải quyết vấn đề, không được chỉ liệt kê các gạch đầu dòng bề mặt. Bắt buộc phải phân tích và so sánh rõ:
     - Ưu điểm (Pros) & Nhược điểm (Cons).
     - Tác động dài hạn tới kiến trúc, hiệu năng và tính bảo trì (Maintenance overhead).
     - Rủi ro phát sinh lỗi phụ (Side-effects & Regressions).
     - Nêu rõ khuyến nghị kỹ thuật có luận cứ vững chắc nhất thay vì phó mặc cho người dùng tự mò mẫm.
6. **Tiêu chuẩn giao tiếp kỹ thuật & Tránh hỏi vội vã (High-Leverage Inquiries)**:
   - Tuyệt đối không đưa ra các câu hỏi/đề xuất nhanh nhảu, hấp tấp kiểu "có muốn fix ngay lỗi này theo cách A không" khi chưa đánh giá toàn diện.
   - Khi cần tham vấn người dùng, câu hỏi phải ở mức tối ưu, tập trung vào quyết định thiết kế kiến trúc then chốt hoặc ranh giới nghiệp vụ, đi kèm đầy đủ bối cảnh đánh đổi (trade-offs).

---

## 3. Nguyên tắc & Tiêu chuẩn lập trình (Coding Standards & Android Invariants)

1. **Kiến trúc phân lớp rõ ràng (Separation of Concerns)**:
   - **Engine Core** (`com.cream.translator.core`):
     - `ScreenCaptureManager`: Chụp khung hình qua `MediaProjection` VirtualDisplay. Sử dụng coroutines non-blocking và xử lý bitmap an toàn.
     - `TextInpainter`: Tính toán màu sắc tương phản độc lập với Android framework, tự động co giãn font theo bounding box.
     - `MlKitOcrEngine`: Nhận dạng chữ trên thiết bị với Google ML Kit Text Recognition độc lập (`com.google.mlkit:text-recognition`).
   - **Service Layer** (`com.cream.translator.service`):
     - `OverlayService`: Foreground service quản lý toàn bộ vòng đời ứng dụng trên màn hình, state machine và các floating control view.
   - **Overlay UI** (`com.cream.translator.ui.overlay`):
     - `InPlaceTranslationOverlay`: Custom View hiển thị văn bản đè lên đúng tọa độ gốc với `StaticLayout`. Luôn thiết lập `isClickable = true` và tiêu thụ `ACTION_DOWN` để tránh xung đột cử chỉ với hệ thống.
   - **Self-Update Engine** (`com.cream.translator.update`):
     - `UpdateEngine`: Truy vấn trực tiếp GitHub Releases API không qua thư viện ngoài, bảo mật với HTTPS-only, xử lý redirect 307/308, kiểm tra dung lượng stream `.tmp` và xác thực mã băm SHA-256.
2. **Quản lý bộ nhớ & Phòng chống rò rỉ (Memory Leak Prevention)**:
   - **Giải phóng Bitmap triệt để**: Luôn gọi `bitmap.recycle()` đối với mọi bitmap trung gian (frame capture, cropped regions) ngay khi hoàn tất xử lý để tránh làm tràn Native Graphic RAM.
   - **Quản lý CoroutineScope**: Mọi scope trong Service hoặc View phải được hủy (`scope.cancel()`) khi Service bị dừng (`onDestroy()`) hoặc View tách khỏi Window (`onDetachedFromWindow()`).
   - **Tránh rò rỉ Context**: Không lưu trữ biến tĩnh (`static` / `companion object`) tham chiếu tới `Context`, `Activity`, `Service` hoặc `View`.
3. **Quy chuẩn bất biến trên Android 14+ (API 34+)**:
   - Khi sử dụng `MediaProjection`, **bắt buộc phải đăng ký `MediaProjection.Callback` TRƯỚC KHI gọi `createVirtualDisplay`**. Vi phạm điều này sẽ dẫn tới `SecurityException` hoặc crash tức thì trên Android 14+.
   - Foreground Service có `foregroundServiceType="mediaProjection"` phải được khởi chạy với đầy đủ quyền và Notification hợp lệ.
4. **Quy chuẩn định dạng mã nguồn & Phân tích tĩnh (Code Style & Linting)**:
   - **Ngôn ngữ**: Kotlin 1.9.x / Java 17.
   - **Code Style**: Tuân thủ Kotlin Official Style, được kiểm tra nghiêm ngặt bởi Ktlint (`1.2.1`) thông qua Spotless plugin.
   - Tự động format toàn bộ codebase trước khi commit:
     ```bash
     ./gradlew spotlessApply
     ```
   - **Detekt**: Phân tích tĩnh đa luồng (`parallel = true`), giới hạn phạm vi quét trong `src/main/java`.
   - **Android Lint**: Tối ưu hóa thời gian quét bằng cách bỏ qua test sources và generated sources.
5. **Tiêu chuẩn kiểm thử tự động (Unit Testing Standards)**:
   - Logic nghiệp vụ trong Unit Test phải là Kotlin thuần túy (pure JVM) hoặc sử dụng mock framework.
   - Cấu hình `testOptions { unitTests.isReturnDefaultValues = true }` đã được bật.
   - **Tuyệt đối không gọi các method unmocked của Android framework** (ví dụ `android.graphics.Color.rgb()`) trong JVM unit test; phải sử dụng phép toán dịch bit chuẩn:
     ```kotlin
     val red = (color shr 16) and 0xFF
     val green = (color shr 8) and 0xFF
     val blue = color and 0xFF
     ```

---

## 4. Quản lý phiên bản ngữ nghĩa (Semantic Versioning Standards)

Tuyệt đối không hardcode chuỗi phiên bản trong mã nguồn Kotlin hay shell script.

- **Nguồn chân lý duy nhất (Single Source of Truth)**: Định nghĩa trong [gradle.properties](file:///d:/Workspaces/Tools/CreamTranslator/gradle.properties):
  ```properties
  appVersionMajor=1
  appVersionMinor=1
  appVersionPatch=0
  ```
- **Semantic Version Code**:
  ```kotlin
  val appVersionCode = (major * 10000) + (minor * 1000) + patch
  ```
  Tăng đơn điệu theo các bản release chính thức.
- **Semantic Version Name**:
  ```kotlin
  val appVersionName = "$major.$minor.$patch"
  ```
  Các bản build debug sẽ tự động nhận thêm hậu tố `-debug` (ví dụ `1.1.0-debug`) và package `com.cream.translator.debug` để không xung đột với bản cài đặt chính thức.
- Kiểm tra phiên bản hiện tại từ dòng lệnh:
  ```bash
  ./gradlew -q printAppVersion
  ```

---

## 5. Quy trình CI/CD & Cơ chế Release tự động

### Mô hình kiểm định song song (Parallel CI Execution)
- Kích hoạt khi có `pull_request` vào `main`.
- Các PR chỉ chỉnh sửa tài liệu (`*.md`, `docs/**`) sẽ tự động được gán `fast-pass` để hoàn thành trong ~2 giây nhằm thỏa mãn branch protection.
- Hai job bắt buộc chạy song song trên RAM Disk (`tmpfs` 2GB):
  1. `Code Quality & Unit Tests`: Spotless, Detekt, LintDebug, TestDebugUnitTest (~1 phút).
  2. `Build Assemble Debug APK`: Biên dịch APK Debug (~1 phút 30 giây).

### Cơ chế Release điều khiển bằng Git Tag (Tag-Driven Release)
- Việc merge PR vào `main` **KHÔNG tự động tạo bản release**, giúp tránh lạm phát số phiên bản khi phát triển.
- Bản phát hành sản phẩm chỉ được tạo ra khi có Git Tag dạng `v*.*.*` hoặc kích hoạt thủ công qua `workflow_dispatch`.
- Quá trình đóng gói release biên dịch trực tiếp trên RAM Disk, tính mã băm SHA-256 và đính kèm binary APK lên GitHub Releases.

---

## 6. Cheatsheet quy trình làm việc chuẩn dành cho Agent (Workflow Cheatsheet)

Khi được giao nhiệm vụ triển khai tính năng, sửa lỗi hoặc tối ưu hóa, Agent phải tuân thủ nghiêm ngặt quy trình 7 bước sau:

```bash
# 1. Cập nhật nhánh main mới nhất và tạo feature branch
git checkout main
git pull origin main
git checkout -b <type>/<ten-tinh-nang-ngan-gon>

# 2. Thực hiện sửa đổi mã nguồn và kiểm tra cục bộ
# ... Chỉnh sửa code ...

# 3. Quản trị tri thức: Nếu phát sinh ý tưởng mới chưa làm -> tạo RFC; nếu đổi kiến trúc -> tạo/cập nhật ADR
# (Tuân thủ theo mẫu trong docs/templates/ và cập nhật vào docs/README.md)

# 4. Format mã nguồn tự động (nếu môi trường có Java) và ủy thác kiểm tra chất lượng cho CI
# (Agent không cần tự chạy full test cục bộ vì CI Actions sẽ tự động kiểm định song song trên RAM disk)
./gradlew spotlessApply || true

# 5. Commit bằng Conventional Commits
git add .
git commit -m "<type>(<pham-vi>): <mo-ta-ngan-gon-bang-tieng-anh-hoac-viet>"

# 6. Đẩy nhánh lên remote và mở Pull Request
git push -u origin <type>/<ten-tinh-nang-ngan-gon>
gh pr create --title "<type>(<pham-vi>): <tieu-de>" --body "<Mo-ta-chi-tiet-thay-doi>"

# 7. Giám sát các CI checks trên PR cho tới khi toàn bộ xanh
gh pr checks <pr-number> --watch

# 8. Báo cáo kết quả và DỪNG LẠI (Chờ người dùng review & quyết định merge)
# TUYỆT ĐỐI KHÔNG tự chạy 'gh pr merge' trừ khi có lệnh tường minh từ người dùng!
```

---

## 7. Tiêu chuẩn Quản trị Tri thức & Tài liệu hóa Kỹ thuật (Technical Documentation Architecture)

Để đảm bảo dự án phát triển bền vững, không tích lũy nợ kỹ thuật và không bị trôi dạt các ý tưởng cải tiến, mọi Agent và lập trình viên phải tuân thủ kiến trúc tài liệu phân tầng tại thư mục `docs/`:

### 1. Phân định Ranh giới ADR và RFC
- **Architecture Decision Record (ADR)** (`docs/adr/ADR-xxx-<ten-quyet-dinh>.md`):
  - **Mục đích**: Ghi nhận các quyết định kiến trúc, lựa chọn công nghệ hoặc ranh giới bất biến đã được áp dụng chính thức vào mã nguồn.
  - **Khi nào viết**: Bắt buộc tạo mới khi thay đổi công nghệ nền tảng, cơ chế đồng bộ luồng, kiến trúc bảo mật (ví dụ: Anti-Cheat) hoặc quy chuẩn thiết kế hệ thống.
  - **Trạng thái**: `Proposed` ➔ `Accepted` ➔ `Superseded`.
  - **Biểu mẫu chuẩn**: [docs/templates/TEMPLATE_ADR.md](file:///d:/Workspaces/Tools/CreamTranslator/docs/templates/TEMPLATE_ADR.md).
- **Request for Comments / Proposal (RFC)** (`docs/proposals/RFC-xxx-<ten-de-xuat>.md`):
  - **Mục đích**: Đặc tả chi tiết các ý tưởng tính năng mới, cải tiến trải nghiệm người dùng (UX) hoặc tái cấu trúc kỹ thuật được phát hiện trong quá trình trao đổi nhưng chưa triển khai ngay trong task hiện tại.
  - **Khi nào viết**: Bắt buộc tạo mới khi người dùng hoặc Agent đề xuất một giải pháp/ý tưởng có giá trị nhưng quyết định để dành cho tương lai, nhằm tránh lạm phát phạm vi (scope creep) mà vẫn bảo toàn tri thức thiết kế.
  - **Trạng thái**: `Proposed` ➔ `Accepted` ➔ `Implemented` / `Rejected` / `Superseded`.
  - **Biểu mẫu chuẩn**: [docs/templates/TEMPLATE_RFC.md](file:///d:/Workspaces/Tools/CreamTranslator/docs/templates/TEMPLATE_RFC.md).

### 2. Quy tắc Đặt tên & Đánh số Thứ tự
- Định dạng bắt buộc: `<LOAI>-<XXX>-<ten-ngan-gon-kebab-case>.md` (với `<XXX>` là số thứ tự 3 chữ số tăng đơn điệu: `001`, `002`, `003`...).
- Ví dụ:
  - `docs/adr/ADR-001-on-device-mlkit-pipeline.md`
  - `docs/proposals/RFC-001-floating-quick-action-menu.md`

### 3. Đồng bộ Mục lục Tri thức Trung tâm
- Mọi bản ADR hoặc RFC khi được tạo mới hoặc thay đổi trạng thái đều phải được cập nhật ngay lập tức vào bảng mục lục tra cứu trung tâm tại [docs/README.md](file:///d:/Workspaces/Tools/CreamTranslator/docs/README.md).

