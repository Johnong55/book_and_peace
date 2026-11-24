# Book & Peace (Android Native — Kotlin + Jetpack Compose)

Ứng dụng đọc sách cá nhân & tạo sách nói AI kể chuyện theo phong cách sâu sắc, hóm hỉnh (cảm hứng từ kênh **Văn Vở** `@xinchaotoilavanvo`).

## Tính năng chính

1. **Kho Sách Mở & Nhập Sách Cá Nhân:**
   - Tích hợp sẵn **Wikisource Tiếng Việt** (Nam Cao, Vũ Trọng Phụng, Ngô Tất Tố, Thạch Lam, Nguyễn Du, Tự Lực Văn Đoàn, Hồ Biểu Chánh...) và **Project Gutenberg** (70.000+ sách kinh điển thế giới).
   - Hỗ trợ **OPDS Catalog** (Standard Ebooks, Gutenberg OPDS và tự thêm URL OPDS tùy ý).
   - **Nhập file `.epub`, `.pdf`, `.txt`** hoặc **quét cả thư mục sách** có sẵn trên điện thoại.
2. **Trình Đọc Sách Thư Thái:**
   - 3 chủ đề giấy đọc: *Giấy ấm (Warm Paper)*, *Giấy cổ (Sepia)*, *Ban đêm (Dark)*.
   - Tùy chỉnh cỡ chữ, giãn dòng, font Serif/Sans, mục lục chương, đánh dấu trang (Bookmark) và tự lưu tiến độ đọc.
3. **AI Kể Chuyện "Chế độ Văn Vở" (3 Phương án 0 VNĐ):**
   - **Google AI Studio Free API Key (Khuyên dùng — Chạy ngầm 100%, 0 VNĐ):** Không cần gói Pro, không cần thẻ tín dụng. App dùng `WorkManager` chạy ngầm đọc toàn bộ sách (1M token context + tự động Map-Reduce cho sách quá dày) để sinh kịch bản 5 hồi đúng chất Văn Vở.
   - **Gemini Nano On-Device (`ML Kit GenAI`):** Chạy ngầm 100% offline trên các thiết bị Android 14+ hỗ trợ AICore.
   - **Liên kết App Gemini trên điện thoại (2 chạm, không cần API Key):** Đóng gói sẵn Prompt Văn Vở → mở App Gemini có sẵn → Copy kết quả quay lại dán là app tự chia hồi và thu âm.
4. **Sách Nói Chạy Nền (`Media3 / ExoPlayer`) & 3 Tầng TTS:**
   - **Gemini 3.8 Flash TTS (`v1beta`):** Giọng kể truyền cảm (mặc định `Charon`), tự động lưu `.wav` cache offline.
   - **Android System TTS (`vi-VN`):** Miễn phí 100%, không cần API Key, tự động làm tầng dự phòng offline.
   - **Custom OpenAI-compatible TTS Endpoint:** Hỗ trợ máy chủ TTS tự host (như VieNeu-TTS, Kokoro, Piper).
   - Trình phát sách nói chạy nền ngay cả khi tắt màn hình, chạy chữ đồng bộ từng đoạn kịch bản, chỉnh tốc độ (`0.8x–2.0x`), hẹn giờ tắt ngủ (*Sleep Timer*).

## Thiết lập Google AI Studio Free API Key (Miễn phí 0 VNĐ)

Bạn có **2 cách** (chọn 1 trong 2):

1. **Thiết lập trực tiếp ngay trong App (Khuyên dùng):**
   - Mở tab **Cài đặt** → bấm **"Bước 1: Mở Google AI Studio tạo Key 0đ"** (`https://aistudio.google.com/apikey`).
   - Đăng nhập Gmail thường → bấm **Create API key** → Copy chuỗi `AIza...`.
   - Quay lại app → bấm **Dán Key** → bấm **Lưu & Kiểm tra kết nối API** (app tự dò các model Gemini Flash & TTS tốt nhất khả dụng trên key của bạn).
2. **Nạp sẵn lúc build APK:**
   - Dán key vào file `local.properties`:
     ```properties
     GEMINI_API_KEY=AIza...
     ```
   - Chạy `.\gradlew.bat assembleDebug`.

## Build & Cài Đặt

```powershell
# Chạy unit tests + build APK debug
.\gradlew.bat testDebugUnitTest assembleDebug

# Cài trực tiếp lên điện thoại Android (đã bật USB Debugging)
adb install -r app\build\outputs\apk\debug\app-debug.apk
```
