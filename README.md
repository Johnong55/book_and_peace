# 📚 Book & Peace

<p align="center">
  <strong>Ứng dụng đọc sách Android thuần chuẩn Máy đọc sách (Kindle-style) & Sách nói AI kể chuyện phân tích sâu</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+" />
  <img src="https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/AI-Gemini%20API%20%2B%20On--Device%20Nano-8E75B2?logo=googlegemini&logoColor=white" alt="Gemini AI" />
  <img src="https://img.shields.io/badge/License-MIT-234236" alt="MIT License" />
</p>

---

## ✨ Giới thiệu

**Book & Peace** là dự án mã nguồn mở xây dựng một trình đọc sách tối giản, dịu mắt cho điện thoại Android, kết hợp với **Studio Kể Chuyện Sâu (AI Audiobook)** theo phong cách kể chuyện phân tích sinh động, trầm ấm và hóm hỉnh.

Thay vì dừng ở những đoạn tóm tắt khô khan vài dòng, Book & Peace tập trung vào hai trải nghiệm cốt lõi:
1. **Đọc sách thật êm mắt & mượt mà như máy đọc sách:** Hỗ trợ đọc nguyên trang PDF có ảnh/tranh/biểu đồ gốc, EPUB có hình minh họa, lật trang ngang kiểu Kindle và 5 bảng màu chuẩn nhãn khoa giảm điều tiết mắt.
2. **Nghe sách nói phân tích đầy đủ 0 VNĐ:** Tích hợp sẵn nguồn **14 tập Sách nói Phân tích Đầy đủ (5–6 hồi/tập)** + **38 Bản Tóm Tắt Sâu** (tiết kiệm 95–100% token API), cùng pipeline chạy ngầm bằng **Google AI Studio Free Tier**, **Gemini Nano On-Device** và **Android TTS Offline**.

---

## 🚀 Tính năng nổi bật

### 📖 Trình đọc chuẩn Máy Đọc Sách & Bảo vệ mắt
* **Đọc PDF gốc giữ nguyên ảnh/tranh/dàn trang:** Sử dụng `PdfRenderer` kết hợp `LruCache` nạp trước trang kề bên, lật trang ngang mượt mà, chạm đúp phóng to, tự động phủ màu giấy dịu mắt lên trang PDF.
* **Đọc EPUB / TXT kiểu lật trang ngang (Kindle) hoặc cuộn dọc mượt:** Tự giải nén và hiển thị tranh minh họa nhúng trong file EPUB, tải từng chương trên luồng nền IO nên mở sách dày hàng trăm trang gần như tức thì.
* **5 bảng màu nhãn khoa đồng bộ toàn app (chống chói & chống quầng sáng Halation):**
  * **Giấy Sepia (`#F4ECD8`)** — Mặc định dịu mắt, giảm ánh sáng xanh.
  * **Giấy Kem E-Ink (`#FBF0D9`)** — Thoáng sáng khi đọc ban ngày.
  * **Xanh Dịu Mắt (`#D5EAD6`)** — Nằm giữa quang phổ giúp giảm căng cơ điều tiết.
  * **Than Chì Tối (`#1B1D1C`)** & **Đêm Trầm (`#141B22`)** — Tránh nền đen tuyền `#000000` gây nhòe chữ cho mắt loạn thị.
* **Bộ lọc ánh sáng xanh ấm**, thanh **giảm độ chói (Dimmer)** khi đọc phòng tối, chạm giữa màn hình ẩn thanh công cụ và **nhắc nghỉ mắt 20-20-20**.

### 🏛️ Kho Sách Mở Đã Kiểm Chứng & Nhập Sách Linh Hoạt
* **78 đầu sách Public Domain đã kiểm chứng đầy đủ toàn văn:**
  * **Khoa học & Tri thức (15 tác phẩm ưu tiên tải nhanh):** *Nguồn gốc các loài* (Darwin), *Thuyết tương đối* (Einstein), *Principia* (Newton), *Lịch sử Hóa học ngọn nến* (Faraday), *Hai ngành khoa học mới* (Galileo), *Diễn giải giấc mơ* (Freud), *Phương pháp luận* (Descartes), *Của cải các quốc gia* (Adam Smith)...
  * **Văn học Việt Nam (41 tác phẩm từ Wikisource VN):** Nam Cao, Thạch Lam, Ngô Tất Tố, Hồ Biểu Chánh, Vũ Trọng Phụng, Khái Hưng, Nguyễn Du, Nguyễn Đình Chiểu, Nguyễn Dữ...
  * **Văn học Thế giới (22 kiệt tác tên tiếng Việt 100%):** *Sherlock Holmes*, *Gatsby Vĩ Đại*, *Kiêu hãnh và Định kiến*, *Tội ác và Hình phạt*, *Những người khốn khổ*, *Đồi gió hú*, *Dracula*, *Frankenstein*, *Tom Sawyer*, *80 ngày vòng quanh thế giới*...
* **Nhập sách cá nhân đa dạng:**
  * Chọn file `.epub`, `.pdf`, `.txt` hoặc **quét cả thư mục sách** trên điện thoại.
  * **Tải trực tiếp từ Link Google Drive / Google Docs / URL** (hỗ trợ stream file PDF lớn ra ổ đĩa, không tràn RAM).
  * **Mở trực tiếp từ hệ thống Android (`Mở bằng Book & Peace` / `Chia sẻ → Book & Peace`)** khi tải file từ trình duyệt hoặc Google Drive.

### 🎧 Studio Kể Chuyện Sâu & Sách Nói Chạy Nền
* **Kho Sách Nói Phân Tích Sâu có sẵn (0 token Text AI):** 14 tập sách nói chi tiết 5–6 hồi (*Chí Phèo, Lão Hạc, Tắt đèn, Truyện Kiều, Mắt biếc, Cho tôi xin một vé đi tuổi thơ, Tôi thấy hoa vàng trên cỏ xanh, Sapiens, Nguồn gốc các loài, Thuyết tương đối, Sherlock Holmes, Gatsby Vĩ Đại, Tội ác và Hình phạt, Suối nguồn*) cùng 24 bản tóm tắt sâu bổ sung.
* **3 chế độ AI 0 VNĐ:**
  1. **Google AI Studio Free API Key** (`gemini-3.8-flash`, chạy ngầm qua `WorkManager`, không cần thẻ tín dụng).
  2. **Gemini Nano On-Device** (`ML Kit GenAI Prompt API`, chạy offline trên máy Android 14+ có AICore).
  3. **Liên kết App Gemini trên điện thoại** (đóng gói sẵn prompt Kể Chuyện Sâu → Copy/Dán kịch bản để thu âm).
* **3 tầng TTS & Trình phát nền `Media3 / ExoPlayer`:**
  * **Gemini 3.8 Flash TTS (`v1beta`)** dùng `speechMetadata.style` (chỉ đọc sạch lời kể, giọng `Charon` trầm ấm).
  * **Android System TTS (`vi-VN`)** miễn phí 100%, chạy offline làm tầng dự phòng tự động.
  * **Custom OpenAI-compatible TTS Endpoint** cho máy chủ tự host (VieNeu-TTS, Kokoro, Piper...).
  * Phát nền khi tắt màn hình, **chạy chữ đồng bộ từng hồi**, chỉnh tốc độ (`0.8x–2.0x`), hẹn giờ tắt ngủ (*Sleep Timer*) và cache audio cục bộ.

---

## 🛠️ Công nghệ sử dụng

| Thành phần | Công nghệ |
| :--- | :--- |
| Ngôn ngữ & UI | Kotlin 2.3, Jetpack Compose, Material 3 (`compileSdk 35`, `minSdk 26`) |
| Lưu trữ cục bộ | Room Database (KSP, Migration), DataStore Preferences |
| Xử lý nền & Âm thanh | WorkManager (Foreground Service), AndroidX Media3 (ExoPlayer + MediaSessionService) |
| Đọc & Parse sách | Native Android `PdfRenderer`, `PDFBox-Android`, `EpubParser` (Zip + Jsoup + inline images), Coil |
| AI & Giọng nói | Gemini Developer API (`v1beta` Text + Native TTS), ML Kit GenAI Prompt (Gemini Nano), Android `TextToSpeech` |

---

## ⚙️ Cài đặt & Build từ mã nguồn

### Yêu cầu
* **JDK 17 hoặc JDK 21**
* **Android SDK 35** (hoặc Android Studio Ladybug / Meerkat trở lên)

### Bước 1: Clone dự án & cấu hình `local.properties`
```bash
git clone https://github.com/Johnong55/book_and_peace.git
cd book_and_peace
```

Tạo file `local.properties` từ file mẫu `local.properties.example`:
```properties
sdk.dir=C\:\\Users\\YOUR_USER\\AppData\\Local\\Android\\Sdk
# Tùy chọn: dán Google AI Studio Free API Key (0đ, lấy tại https://aistudio.google.com/apikey)
# File local.properties đã nằm trong .gitignore và KHÔNG BAO GIỜ bị đẩy lên Git.
GEMINI_API_KEY=
```
> **Mẹo:** Bạn có thể để trống `GEMINI_API_KEY` lúc build và dán trực tiếp trong mục **Cài đặt** ngay trên điện thoại sau khi cài app.

### Bước 2: Chạy kiểm thử & Build APK Release mượt tối đa
```bash
# Windows PowerShell
.\gradlew.bat testDebugUnitTest assembleRelease

# macOS / Linux
./gradlew testDebugUnitTest assembleRelease
```

### Bước 3: Cài đặt lên điện thoại Android
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```
*(Khuyên dùng bản `app-release.apk` thay vì `debug` để đạt tốc độ cuộn và lật trang mượt 60–120 FPS nhờ tối ưu ART.)*

---

## 📁 Cấu trúc mã nguồn

```text
app/src/main/
├── assets/
│   ├── deep_summaries_vi.json        # Kho 38 bản tóm tắt sâu tiết kiệm token
│   └── stories/                      # 14 tập sách nói phân tích đầy đủ (5-6 hồi/tập)
│       ├── vn_classics.json
│       ├── nna_universe.json
│       ├── science_classics.json
│       └── world_classics.json
└── java/com/bookandpeace/app/
    ├── data/
    │   ├── ai/                       # GeminiCloudClient, GeminiNanoClient, VanVoPromptBuilder, DeepSummaryRepository
    │   ├── local/                    # Room Entities, DAOs, BookDatabase, UserPreferences
    │   ├── parser/                   # EpubParser (text + inline images), PdfParser, TxtParser
    │   ├── remote/                   # WikisourceClient, GutendexClient, OpdsClient, UrlBookDownloader
    │   └── tts/                      # GeminiTtsEngine, AndroidSystemTtsEngine, CustomTtsEngine, WavUtils
    ├── service/                      # AudiobookPlaybackService (Media3) & AudioPlayerController
    ├── worker/                       # StoryGenerationWorker (WorkManager chạy ngầm)
    └── ui/                           # Library, Discover, Reader (+ PdfBookPager), Storyteller, Player, Settings, Theme
```

---

## 🗺️ Lộ trình phát triển cộng đồng (Roadmap)

- [ ] Mở rộng kho kịch bản phân tích sách nói cộng đồng (`assets/stories/*.json`) lên 50+ tác phẩm Khoa học, Triết học và Văn học.
- [ ] Hỗ trợ đồng bộ tiến độ đọc & ghi chú (highlight) ra file Markdown / Backup cục bộ.
- [ ] Thêm từ điển tra cứu nhanh (Anh–Việt / Việt–Việt) ngay khi nhấn giữ từ trong trang đọc.
- [ ] Hỗ trợ chế độ đọc 2 trang ngang cho máy tính bảng Android (Tablet / Foldable).
- [ ] Thêm tùy chọn kết nối Local LLM (Ollama / LM Studio qua Wi-Fi nội bộ).

---

## 🤝 Đóng góp cho dự án (Contributing)

Mọi đóng góp từ cộng đồng đều được hoan nghênh — từ sửa lỗi, tối ưu hiệu năng đến **đóng góp thêm kịch bản phân tích sách nói sâu** trong `app/src/main/assets/stories/`!

Xem hướng dẫn chi tiết cách tạo Pull Request và cấu trúc file JSON tại [CONTRIBUTING.md](CONTRIBUTING.md).

---

## 📄 Giấy phép

Dự án được phát hành theo giấy phép **MIT License** — xem chi tiết tại [LICENSE](LICENSE).

> **Lưu ý về bản quyền sách:** Kho sách tích hợp sẵn trong ứng dụng chỉ lấy từ các nguồn **Public Domain / Mở** (Wikisource, Internet Archive). Với các tác phẩm còn bản quyền, dự án chỉ cung cấp bài viết phân tích / bình luận độc lập và khuyến khích độc giả mua sách bản quyền để ủng hộ tác giả và nhà xuất bản.
