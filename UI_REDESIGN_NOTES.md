# Ghi chú làm lại giao diện

## Phạm vi

Bản này làm mới toàn bộ UI của ứng dụng `YEU EM MOI VU TRU` mà không thay đổi logic xử lý file TXT, trợ năng, auto typing hoặc floating widget.

## Thay đổi chính

- Chuyển sang ngôn ngữ hình ảnh **dark navy + mint/coral** với nền sâu, thẻ nội dung bo góc lớn và viền nhẹ.
- Tổ chức màn hình chính thành bốn bước: **Quyền truy cập**, **Nội dung**, **Thiết lập**, **Điều khiển**.
- Viết lại header, trạng thái trợ năng, vùng file đang chọn, các trường thiết lập, thẻ hướng dẫn volume và cụm START/STOP.
- Đồng bộ menu nổi với giao diện chính: panel compact, pill trạng thái, danh sách file có vùng chạm rõ, Spinner và nút biểu tượng mới.
- Thêm template Spinner cho cả trạng thái đang chọn và menu xổ xuống.
- Giữ nguyên toàn bộ ID view được Activity và Service sử dụng.

## Kiểm thử

- Đã kiểm tra cú pháp **41 XML files**.
- Đã xác nhận toàn bộ ID bắt buộc của `MainActivity` và `FloatingWidgetService` vẫn tồn tại.
- Đã xác nhận các color/drawable được layout mới tham chiếu đều tồn tại.
- `assembleDebug` chưa chạy được trong sandbox vì môi trường không có Android SDK; hãy mở project bằng Android Studio có SDK phù hợp để build APK cuối cùng.

## Cập nhật menu nổi

Menu nổi đã được nâng cấp thêm với ba nút hành động lớn có nền riêng cho START, PAUSE và STOP, icon vector tùy biến, nhãn hành động và trạng thái nhấn. Nút PAUSE tự đổi thành RESUME cùng icon tương ứng khi tạm dừng. Nút mở menu, thu gọn và đóng menu cũng đã chuyển sang icon vector mới; danh sách file động có hàng tương tác và nền riêng.

## Tối ưu chuyển động

Animation nhấn nút hiện dùng nhịp scale nhẹ, giảm alpha ngắn và overshoot khi trả về kích thước gốc. Menu nổi có hiệu ứng mở bằng fade, scale và dịch chuyển dọc; khi thu gọn sẽ chạy exit animation trước khi remove khỏi WindowManager. Trạng thái trên menu nổi đổi bằng crossfade kết hợp scale nhẹ, danh sách file xuất hiện tuần tự với độ trễ ngắn, và nút PAUSE/RESUME đổi icon cùng nhãn theo trạng thái.

## Thư viện file và nhận diện thương hiệu

Tên ứng dụng trên màn hình chính hiện là **Auto Typer** với gradient nhiều màu chuyển động liên tục; tên tác giả cũng dùng gradient chuyển động riêng. Mỗi file TXT được import sẽ tự lưu vào thư viện nội bộ và tự được tick sau khi lưu. Người dùng có thể tick hoặc bỏ tick nhiều file trong màn hình chính hoặc menu nổi; khi START, nội dung của toàn bộ file đang tick sẽ được ghép theo thứ tự tên file. Các câu chữ về mục đích giải trí đã được loại bỏ khỏi phần app resources.

## Nhận diện và thông báo

Tên ứng dụng đã đổi thành **Con Me May**, launcher icon dùng ảnh logo anime do người dùng cung cấp. Toàn bộ Toast trong `app/src` đã được loại bỏ; các trạng thái cần thiết được giữ bằng cập nhật giao diện hoặc Log nội bộ. Bộ kiểm tra tĩnh xác nhận 50 tệp XML hợp lệ, không còn Toast, đúng tên app và đúng launcher logo. Build APK đầy đủ trong sandbox chưa thực hiện được vì môi trường không có Android SDK.
