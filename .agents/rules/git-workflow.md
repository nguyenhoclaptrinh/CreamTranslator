# Git Workflow & Pull Request Governance

Tài liệu này định nghĩa quy trình Git 7 bước chuẩn mực và các bất biến bảo vệ nhánh bắt buộc đối với mọi AI Agent.

---

## 1. Bất biến Bảo vệ Nhánh (Branch Protection Invariants)

1. **Nhánh `main` được bảo vệ tuyệt đối**:
   - `enforce_admins: true`.
   - **CẤM TUYỆT ĐỐI**: Không bao giờ commit hoặc push trực tiếp lên nhánh `main`.
   - Mọi thay đổi bắt buộc phải đi qua feature branch và Pull Request (PR) nhắm vào `main`.

2. **Ranh giới Bàn giao & Thẩm quyền Merge PR**:
   - Agent **CHỈ ĐƯỢC PHÉP**: Tạo PR, kiểm tra tính đồng bộ với `main`, giám sát các checks CI chạy xanh hoàn toàn và báo cáo kết quả cho người dùng.
   - **DỪNG LẠI TẠI ĐÂY**: Chờ người dùng tự review và quyết định.
   - **TUYỆT ĐỐI KHÔNG TỰ MERGE PR** (`gh pr merge`) trừ khi có mệnh lệnh rõ ràng, tường minh từ người dùng.

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
