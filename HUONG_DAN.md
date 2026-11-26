# Dịch Game Hàn (Android 9 trở lên)

## A. Tạo file APK bằng GitHub (miễn phí với kho công khai)
1. Giải nén file zip trên máy tính.
2. Đăng nhập github.com, bấm New repository, đặt tên DichGameHan, bấm Create.
3. Chọn "uploading an existing file", kéo toàn bộ nội dung BÊN TRONG thư mục DichGameHan vào, bấm Commit changes.
4. Kiểm tra trong kho có thư mục .github chưa (thư mục ẩn hay bị bỏ sót). Nếu chưa:
   Add file > Create new file, gõ tên .github/workflows/build.yml,
   dán nội dung file build-workflow-copy.txt, bấm Commit changes.
5. Mở tab Actions > Build APK (nếu chưa chạy, bấm Run workflow). Đợi dấu tích xanh.
6. Bấm vào lần chạy đó, tải DichGameHan-apk ở mục Artifacts, giải nén được app-debug.apk.

Cách khác: mở thư mục bằng Android Studio rồi chọn Build > Build APK(s).

## B. Cài và dùng
1. Chép app-debug.apk vào điện thoại, mở file, cho phép cài từ nguồn này.
2. Mở app, bấm "Tải gói Hàn - Việt" (cần mạng, một lần).
3. Bấm "Bắt đầu", cấp quyền hiển thị trên ứng dụng khác và quyền chụp màn hình.
4. Trong game, chạm nút "Dịch". Kéo nút để đổi chỗ. Chạm màn hình để đóng bản dịch.
5. Dừng: chạm thông báo, hoặc bấm "Dừng" trong app.

## Giới hạn
- Dịch trên máy bằng Google ML Kit, đi qua tiếng Anh (Hàn > Anh > Việt) nên câu có thể thiếu tự nhiên.
- Chữ cách điệu, chữ nhỏ hoặc nền rối có thể đọc sai.
- Một số game chặn chụp màn hình (ảnh đen) hoặc cấm ứng dụng hiển thị đè. Hãy xem điều khoản của game.
- Bản cài mới tạo bằng GitHub có thể báo xung đột chữ ký: gỡ bản cũ rồi cài lại.
