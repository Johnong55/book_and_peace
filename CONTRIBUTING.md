# Đóng góp cho Book & Peace (Contributing Guide)

Cảm ơn bạn đã quan tâm và muốn chung tay phát triển **Book & Peace**! Mọi đóng góp — dù là một đoạn code tối ưu, báo lỗi, cải tiến giao diện hay viết thêm một tập phân tích sách nói sâu — đều vô cùng quý giá.

---

## 1. Các cách đóng góp dễ nhất

1. **Đóng góp Tập Sách Nói Phân Tích Sâu mới (Không cần biết code Android!):**
   * Các kịch bản nằm trong `app/src/main/assets/stories/*.json`.
   * Yêu cầu nội dung:
     * Chia thành **4 đến 7 hồi** (`narrationParts`), tổng độ dài từ **1.000 từ trở lên**.
     * Viết bằng **văn nói tiếng Việt** cuốn hút, có tình tiết rõ ràng và lồng ghép **góc nhìn đánh giá, phân tích nghệ thuật/khoa học độc lập của riêng bạn**.
     * **Tuyệt đối không sao chép nguyên văn** nội dung từ sách có bản quyền hay bài viết của người khác.
     * Không chứa dấu markdown (`**`, `#`, gạch đầu dòng) trong trường `content` để máy TTS đọc mượt nhất.
   * Cấu trúc mẫu một mục JSON:
     ```json
     {
       "id": "ten_tac_pham_khong_dau",
       "titleVi": "Tên tác phẩm tiếng Việt",
       "author": "Tên tác giả",
       "category": "Khoa học | Văn học VN | Văn học Thế giới | Nguyễn Nhật Ánh",
       "matchKeys": ["ten tac pham", "original title"],
       "context": "Bối cảnh ra đời ngắn gọn.",
       "charactersOrCoreConcepts": "Nhân vật hoặc khái niệm cốt lõi.",
       "deepAnalysis": "Tóm lược ngắn mạch phân tích.",
       "keyDetailsAndQuotes": "Điểm nhấn đắt giá.",
       "vanVoPerspective": "Một câu nhận định đọng lại.",
       "narrationParts": [
         {
           "title": "Tên hồi 1 hấp dẫn",
           "toneHint": "warm, intriguing, expressive Vietnamese storytelling",
           "content": "Nội dung kể chuyện phân tích chi tiết của hồi 1..."
         }
       ]
     }
     ```

2. **Đóng góp tính năng & sửa lỗi Android (Kotlin / Jetpack Compose):**
   * Fork repo → tạo nhánh mới từ `main` (`feat/ten-tinh-nang` hoặc `fix/ten-loi`).
   * Đảm bảo chạy pass toàn bộ unit tests trước khi mở Pull Request:
     ```bash
     ./gradlew testDebugUnitTest assembleRelease
     ```

3. **Báo lỗi & Đề xuất tính năng:**
   * Mở GitHub Issue mô tả rõ: dòng máy Android, phiên bản Android, các bước tái hiện lỗi và ảnh chụp màn hình (nếu có).

---

## 2. Quy tắc bảo mật & Bản quyền bắt buộc

* **KHÔNG BAO GIỜ commit API Key**, token cá nhân, file `local.properties` hoặc file `.keystore` vào Git.
* **KHÔNG gắn cứng link/crawler tới các trang phát tán sách lậu vi phạm bản quyền** trong mã nguồn dự án. Kho mặc định chỉ chấp nhận nguồn mở Public Domain (Wikisource, Project Gutenberg, Internet Archive mở, OPDS hợp lệ).
