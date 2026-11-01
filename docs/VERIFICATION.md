# Kiểm chứng bộ khung

## Đã chạy tại máy phát triển

- `gradlew.bat --no-daemon test bootJar`: thành công; 3 kiểm thử API/security pass.
- `pnpm build`: TypeScript và Vite build thành công.
- `docker compose --profile app config --quiet`: cấu hình hợp lệ.
- Dev server trả HTTP 200; kiểm tra trang giới thiệu và điều hướng `/admin` trong trình duyệt.
- Kiểm tra trạng thái API chưa sẵn sàng: frontend vẫn hiển thị nội dung và hướng dẫn, không trắng trang.

## Kiểm chứng qua CI

Workflow `.github/workflows/ci.yml` có ba job:

1. Backend build + kiểm thử API + PostgreSQL/pgvector/Redis Session.
2. Frontend cài dependency theo lockfile + TypeScript + build.
3. Docker Compose build/khởi động toàn stack + smoke test frontend, API proxy và health.

Xem kết quả thực tế tại [GitHub Actions](https://github.com/hoaqhienn/petfood-store-www-java-group10/actions). Cấu hình workflow không đồng nghĩa workflow đã chạy thành công.

## Giới hạn

Môi trường thao tác local hiện không truy cập được Docker Engine, nên chưa xác nhận runtime full stack trên chính máy Windows này. Chưa có kiểm thử gửi email thật, AI hoặc nghiệp vụ mua hàng vì các chức năng đó chưa được triển khai.

