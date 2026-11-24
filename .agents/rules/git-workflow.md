# Git Workflow & Pull Request Governance

Tài liệu này định nghĩa quy trình Git 7 bước chuẩn mực và các bất biến bảo vệ nhánh bắt buộc đối với mọi AI Agent.

---

## 1. Bất biến Bảo vệ Nhánh (Branch Protection Invariants)

1. **Nhánh `main` được bảo vệ tuyệt đối**:
   - `enforce_admins: true`.
   - **CẤM TUYỆT ĐỐI**: Không bao giờ commit hoặc push trực tiếp lên nhánh `main`.
   - Mọi thay đổi bắt buộc phải đi qua feature branch và Pull Request (PR) nhắm vào `main`.

2. **Quy trình Review, Fix & Merge PR qua Subagent**:
   - Sau khi tạo PR và CI chạy xanh, **luôn sử dụng subagent** (`invoke_subagent` với `TypeName: "self"`, `Workspace: "share"`) để review PR, sửa các finding phát hiện được, xác minh lại CI và merge PR:
     - Prompt chuẩn cho subagent: `"Hãy review PR <pr-id> và fix các finding, sau đó merge PR."`
   - Nếu không có lệnh merge hoặc người dùng yêu cầu giữ PR ở trạng thái chờ review thủ công, dừng lại sau khi CI xanh và báo cáo kết quả.
   - Sau khi subagent merge PR thành công, đồng bộ lại nhánh `main` cục bộ (`git pull origin main`) và bất kỳ worktree nào đang hoạt động.

---

## 2. Quy trình Thực thi 7 Bước Chuẩn mực

```bash
# Bước 1: Đồng bộ nhánh main và rẽ nhánh tính năng
git checkout main
git pull origin main
git checkout -b <type>/<ten-tinh-nang-ngan-gon>

# Bước 2: Chỉnh sửa mã nguồn và chạy xác thực cục bộ
# ... Chỉnh sửa code ...

# Bước 3: Quản trị tri thức (Tạo RFC nếu có ý tưởng mới; tạo/cập nhật ADR nếu đổi kiến trúc)
# Đồng bộ ngay vào docs/README.md

# Bước 4: Tự động định dạng mã nguồn
./gradlew spotlessApply || true

# Bước 5: Tạo commit tuân thủ Conventional Commits
git add .
git commit -m "<type>(<pham-vi>): <mo-ta-ngan-gon>"

# Bước 6: Đồng bộ nhánh với main mới nhất trước khi mở PR
git fetch origin main
git merge origin/main

# Bước 7: Đẩy nhánh lên remote và tạo Pull Request
git push -u origin <type>/<ten-tinh-nang-ngan-gon>
gh pr create --title "<type>(<pham-vi>): <tieu-de>" --body "<Mo-ta-chi-tiet-thay-doi>"

# Giám sát CI cho tới khi toàn bộ checks đạt trạng thái pass
gh pr checks <pr-number> --watch
```
