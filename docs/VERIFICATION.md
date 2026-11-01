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

**Cả ba job đã pass** cho commit `d2c24b0398a9e8f7720f902d616a92257059c1de` tại [lần chạy CI 41328277688](https://github.com/hoaqhienn/petfood-store-www-java-group10/actions/runs/41328277688). Full-stack Docker đã khởi động và smoke test thành công trên GitHub runner Linux. Những thay đổi sau đó cần xem kết quả CI tương ứng.

## Giới hạn

Môi trường thao tác local hiện không truy cập được Docker Engine, nên chưa xác nhận runtime full stack trên chính máy Windows này. Chưa có kiểm thử gửi email thật, AI hoặc nghiệp vụ mua hàng vì các chức năng đó chưa được triển khai.

