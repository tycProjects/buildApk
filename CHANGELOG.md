# 🎉 YEU EM MOI VU TRU - Update v2.4

## ✨ Cải tiến mới (v2.4):

### 🚀 Tối ưu file lớn (10k+ dòng)
- **Không còn crash** với file .txt hàng chục nghìn dòng
- Bỏ gửi payload qua Intent (tránh TransactionTooLargeException)
- Load file + build tin nhắn trên **background thread** (không đơ UI)
- Dùng List tĩnh truyền data sang service
- Buffer đọc file 64KB, hỗ trợ largeHeap
- Hiển thị "Đang đọc file..." / "Đang chuẩn bị..." khi xử lý file lớn

---

# 🎉 YEU EM MOI VU TRU - Update v2.3

## ✨ Cải tiến mới (v2.3):

### ⌨️ Tùy chọn cách gõ
- Thêm spinner **Cách gõ** trong phần Cài đặt:
  - **Gõ từng chữ** — gõ từng ký tự một (như cũ, delay chữ 50ms)
  - **Dán từng dòng** — dán nguyên từng dòng/tin nhắn một lần rồi gửi
- Chọn chế độ trước khi bấm START
- Hỗ trợ cả Volume ▲ loop lại với đúng chế độ đã chọn

---

# 🎉 YEU EM MOI VU TRU - Update v2.2

## ✨ Cải tiến mới (v2.2):

### 🔒 Chỉ gõ khi bàn phím đang mở
- Ấn **Start** luôn thành công (session bắt đầu ngay)
- Nếu bàn phím chưa mở → app **chờ**, không gõ
- Khi mở bàn phím lên → bắt đầu / tiếp tục gõ ngay
- Nếu đang gõ mà đóng bàn phím → tạm dừng chờ, mở lại thì gõ tiếp đúng chỗ
- Không bị mất tiến độ, không bị bỏ qua tin nhắn

### 🐱 Logo mới
- Thay logo app bằng ảnh mèo đội vỏ chuối

---

# 🎉 YEU EM MOI VU TRU - Update v2.0

## ✨ Các cải tiến chính:

### 1. 📝 Đổi tên ứng dụng
   - App name: `SBR AUTO TYPER` → `YEU EM MOI VU TRU`
   - Package name: `com.sbr.autotyper` → `com.ryan.autotyper`

### 2. 🎨 Thiết kế giao diện hoàn toàn mới
   - Giao diện hiện đại với gradient màu đẹp
   - Các card được trang trí với shadow elevation
   - Font size và spacing được tối ưu hóa
   - Màu sắc nhất quán từ blue/teal sang toàn bộ UI
   - Thêm emoji biểu tượng vào từng phần

### 3. 🎭 Animation & Visual Effects
   - Gradient backgrounds cho header, buttons, input fields
   - Elevation (shadow) trên các card và button
   - Smooth button backgrounds với stroke border
   - Color-coded buttons:
     - 🟢 START button: Green gradient
     - 🔴 STOP button: Red gradient
     - 🔵 Primary buttons: Teal gradient
     - 🟦 Volume hints: Blue gradient

### 4. ⏱️ Hợp nhất Delay thành 1 trường
   - Trước: 2 trường delay (Delay tin + Delay chữ)
   - Sau: 1 trường delay chính cho tin nhắn (delay chữ auto 50ms)
   - Giao diện gọn gàng, dễ sử dụng hơn
   - Icon ⏱️ cho dễ nhận biết

### 5. 🔄 Tính năng Loop (Chạy lại từ đầu)
   - Khi hết tất cả dòng, có thể ấn Volume ▲ để chạy lại từ đầu
   - Toast hiển thị: "✅ Đã gõ xong tất cả — Ấn Volume ▲ để loop lại"
   - Hữu ích cho việc spam/spam messages
   - Bất kì khi nào có thể dùng Volume DOWN để tạm dừng

### 6. ❤️ Chức năng Réo tên ở cuối dòng
   - Thêm trường: "❤️ Réo tên ở cuối dòng (tùy chọn)"
   - Nhập tên/cụm từ bạn muốn réo (VD: "em yêu anh", "baby", "anh yêu em")
   - Tên này sẽ tự động được thêm vào cuối mỗi dòng
   - Nếu không nhập thì gõ bình thường
   - Ví dụ:
     - File: "Đây là dòng 1"
     - Nhập suffix: "yêu anh"
     - Output: "Đây là dòng 1 yêu anh"

## 📱 Các tính năng giữ nguyên:
   - ✅ Accessibility service control
   - ✅ File picker cho file .txt
   - ✅ Spinner chọn số dòng (1-5)
   - ✅ Volume button controls (▲ = continue, ▼ = pause)
   - ✅ STOP button to completely stop

## 🛠️ Hướng dẫn sử dụng:

1. **Bước 1**: Bật quyền Trợ năng (Accessibility)
   - Click "Bật quyền Trợ năng"
   - Tìm và bật "YEU EM MOI VU TRU"

2. **Bước 2**: Chọn file .txt
   - Click "Chọn file .txt"
   - Chọn file text chứa những dòng bạn muốn gõ

3. **Bước 3**: Cài đặt
   - Số dòng mỗi lần gửi (default: 1)
   - Delay tin (delay giữa các tin nhắn): default 1000ms
   - Réo tên (tùy chọn): nhập tên/cụm từ muốn thêm vào cuối

4. **Bước 4**: Chạy
   - Click "▶ START"
   - Chuyển sang app chat (Zalo, Messenger, etc)
   - Ấn Volume ▲ để tiếp tục, Volume ▼ để tạm dừng
   - Ấn STOP để tắt hoàn toàn

## 🎨 Các thay đổi drawable:
   - bg_gradient.xml: Background chính (Blue gradient)
   - bg_header_gradient.xml: Header (Teal gradient)
   - bg_card_gradient.xml: Card backgrounds (Dark blue)
   - bg_btn_primary_gradient.xml: Primary buttons (Teal)
   - bg_btn_start_gradient.xml: Start button (Green)
   - bg_btn_stop_gradient.xml: Stop button (Red)
   - bg_input_gradient.xml: Input fields (Dark)
   - bg_volume_hint_gradient.xml: Volume hints (Blue)

## 📊 Version Info:
   - Version Code: 2
   - Version Name: 2.0
   - Min SDK: 21
   - Target SDK: 34

## 🎯 Future Features (có thể thêm):
   - Lưu các pattern text hay dùng
   - Custom suffix name library
   - Adjust timing per line
   - Theme selector (Dark/Light)

---
Made with ❤️ by Ryan
Chỉ dùng cho mục đích giải trí
