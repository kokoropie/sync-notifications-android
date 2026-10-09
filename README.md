# Sync Notification – Android

<p align="center"><img src="https://raw.githubusercontent.com/kokoropie/sync-notifications/main/assets/logo-bell.png" width="96" alt="Sync Notification"></p>

App Android gửi thông báo và cuộc gọi lên server để hiện trên Mac, kèm đồng bộ clipboard hai chiều. Giao diện cấu hình bằng React Native, phần chạy nền viết bằng Kotlin (NotificationListenerService, PHONE_STATE, WebSocket).

Thuộc hệ thống [Sync Notification](https://github.com/kokoropie/sync-notifications):
[server](https://github.com/kokoropie/sync-notifications-server) · **android** · [mac](https://github.com/kokoropie/sync-notifications-mac)

Yêu cầu: Android 8.0 trở lên (khuyến nghị 10+).

## Cài đặt

1. Tải `SyncNotification-<phiên bản>.apk` ở trang [Releases](../../releases) và cài (cho phép cài từ nguồn này khi hệ thống hỏi).
2. Mở app, nhập **URL server** và **Account key** (`ntf_…`, do người quản trị server tạo bằng `npm run key:create`), bấm **Lưu & kiểm tra kết nối**.
3. Cấp các quyền ở mục **Quyền**:
   - **Truy cập thông báo** (bắt buộc để đọc thông báo)
   - Trạng thái điện thoại + nhật ký cuộc gọi, Danh bạ (hiện tên người gọi)
   - **Trợ năng** (đọc clipboard ở nền, Android → Mac tự động)
   - **Không giới hạn pin** (giữ kết nối nền)
   - Cho phép hiện thông báo dịch vụ (Android 13+)
4. Bấm **Gửi thông báo thử sang Mac** để kiểm tra cả luồng Android → server → Mac.

Một số hãng máy (Xiaomi, Oppo…) cần bật thêm "autostart" cho app.

## Tính năng

- Gửi thông báo của các app lên Mac, kèm icon app (upload một lần cho mỗi app).
- Cuộc gọi đến, cuộc gọi nhỡ, cuộc gọi đã nghe.
- **Whitelist app**: chỉ gửi thông báo của app đã chọn (tắt thì gửi tất cả). Dùng chung với Mac theo account key. Cuộc gọi không bị lọc.
- **Gửi thông báo thử** để kiểm tra kết nối.
- **Clipboard hai chiều**: Mac → Android luôn tự động. Android → Mac tự động khi bật dịch vụ Trợ năng; nếu không, dùng nút trong app, ô Quick Settings "Gửi clipboard", hoặc Chia sẻ → "Gửi sang Mac".
- **Tự cập nhật** (xem bên dưới).

### Giới hạn
- Android 10+ chặn app nền **đọc** clipboard, nên cần dịch vụ Trợ năng hoặc gửi thủ công như trên.
- Số gọi đến cần quyền `READ_CALL_LOG`.

## Tự cập nhật

Khi mở app, mục **Cập nhật** tự kiểm tra GitHub Releases; cũng có nút **Kiểm tra cập nhật** để kiểm tra thủ công. Nếu có bản mới, bấm **Tải và cài đặt**: app tải APK, kiểm tra cùng package và cùng chữ ký với bản đang cài, rồi mở trình cài đặt của hệ thống.

- Lần đầu, Android yêu cầu bật **Cho phép từ nguồn này** cho app; bật xong, quay lại app và bấm cài đặt lần nữa.
- Android luôn hiện hộp thoại xác nhận cài đặt, không thể bỏ qua.
- Mọi bản APK phải ký **cùng một keystore** thì mới cài đè được. APK ký bằng khóa khác (ví dụ bản debug) sẽ bị từ chối với thông báo rõ ràng; khi đó cần gỡ app và cài lại một lần.
- Cần repo có thể đọc công khai trên GitHub; nếu repo private, kiểm tra cập nhật sẽ báo lỗi.

## Tự build

Cần Node 22+, JDK 17 và Android SDK.

```bash
npm install
npm start                      # Metro
npm run android                # chạy bản debug trên thiết bị/emulator
npm run build:debug            # android/app/build/outputs/apk/debug/app-debug.apk
npm run build:release          # APK release (arm64-v8a)
npm run install:release        # adb install -r
npm test                       # jest
```

### Keystore ký bản release
```bash
npm run keystore:create        # tạo android/app/release.keystore + android/keystore.properties (không commit)
```
Có `keystore.properties` thì `assembleRelease` ký bằng khóa này, không thì tạm ký bằng khóa debug. **Giữ keystore an toàn và dùng lại cho mọi bản**: mất hoặc đổi keystore thì người dùng không cập nhật đè được.

## Phát hành bằng GitHub Actions

- Dùng script: `npm run release -- 1.2.0` (hoặc `patch` / `minor` / `major` để tăng từ tag mới nhất; thêm `-n` để chạy thử không tạo tag, `-y` để bỏ xác nhận). Script tạo tag `v1.2.0` và push.
- Hoặc thủ công: push tag dạng `v*` → workflow build APK và đăng lên Releases.
- Chạy tay (`workflow_dispatch`) → build APK, lưu ở mục Artifacts, không phát hành.

Secrets để ký bằng khóa release (thiếu thì workflow cảnh báo và ký bằng khóa debug):

| Secret | Nội dung |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -i android/app/release.keystore \| pbcopy` |
| `ANDROID_KEYSTORE_PASSWORD` | Mật khẩu keystore |
| `ANDROID_KEY_ALIAS` | Alias khóa (mặc định `notify`) |
| `ANDROID_KEY_PASSWORD` | Mật khẩu khóa (mặc định bằng mật khẩu keystore) |

## Quyền riêng tư

- App chỉ kết nối tới **server do bạn cấu hình** (và GitHub để kiểm tra cập nhật).
- Nội dung thông báo, cuộc gọi và clipboard được gửi lên server của bạn; không gửi đi đâu khác.
