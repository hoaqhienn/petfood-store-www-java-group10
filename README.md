# PetFood

**Good food. Happy pets.** Website giới thiệu và bán thức ăn chó mèo, ưu tiên tối giản, nhanh và tiện lợi.

Đồ án **Lập trình WWW (Java)** · Lớp **DHKTPM19ATT** · **Nhóm 10** · **Đề tài 42**.
Thời gian dự kiến: **01–30/11/2026**.

## Trạng thái hiện tại (16/11/2026)

Backend đã đủ phần bắt buộc (P0): luồng mua COD từ giỏ guest đến email xác nhận, và toàn bộ API quản trị (đơn, người dùng, danh mục/sản phẩm, tồn kho, email). Giao diện mới có danh mục và giỏ hàng. AI và tracking (P1) chưa làm.

| Phần | Backend (API) | Frontend |
|---|---|---|
| Hạ tầng: PostgreSQL + pgvector, Redis Session, Flyway, Mailpit, Docker Compose, CI | Có | Có |
| Catalog: danh mục, danh sách có lọc, chi tiết, ẩn sản phẩm ngừng bán | Có | Có |
| Giỏ hàng guest trong Redis Session, revision chống ghi đè, CSRF | Có | Có |
| Đăng ký, đăng nhập/đăng xuất Session, `/me`, phân quyền CUSTOMER/ADMIN | Có | Chưa |
| Xem trước checkout COD, đặt đơn (trừ tồn, chống gửi lặp) | Có | Chưa |
| Email đăng ký/xác nhận đơn qua outbox, tự thử lại | Có | — |
| Lịch sử, chi tiết đơn và hủy đơn `PENDING` của khách | Có | Chưa |
| Quản trị đơn: tìm kiếm, chi tiết, đổi trạng thái COD, sửa số lượng | Có | Chưa (`/admin` giữ chỗ) |
| Quản trị người dùng, danh mục/sản phẩm, điều chỉnh tồn, gửi lại email lỗi | Có | Chưa |
| Tài khoản ADMIN đầu tiên từ cấu hình local | Có | — |
| Hồ sơ thú cưng, RAG/pgvector, tracking, cá nhân hóa | Chưa | Chưa |

Kiểm thử tích hợp (chạy trong CI) đi qua luồng chính giỏ → đăng ký/đăng nhập → xem trước → đặt đơn (kể cả gửi lặp) → lịch sử → hủy đơn, các API quản trị, hai khách tranh mua món cuối, email khi SMTP lỗi và giỏ nhiều tab. Tài khoản ADMIN đầu tiên được tạo từ cấu hình local, xem [LOCAL_DEVELOPMENT.md](docs/LOCAL_DEVELOPMENT.md#9-tài-khoản-quản-trị-đầu-tiên).

Tài liệu thiết kế (03–04/11): 15 chức năng, ma trận quyền, quy tắc COD/tồn kho, API, mô hình dữ liệu, ràng buộc RB01–RB22 trong [CONSTRAINTS.md](docs/CONSTRAINTS.md) và 36 kịch bản nghiệm thu.

## Công nghệ

| Thành phần | Lựa chọn |
|---|---|
| Backend | Java 17, Spring Boot 3.5.16, Gradle Wrapper 8.14.3 |
| Frontend | React, TypeScript, Vite, pnpm 11.25.0 |
| Dữ liệu | PostgreSQL 17, pgvector; Flyway quản lý migration |
| Session | Spring Session + Redis 7.4 |
| Email local | Mailpit; có cấu hình SMTP thật qua biến môi trường |
| Chạy local | Docker Compose; tùy chọn chạy backend/frontend bằng IDE |
| AI dự kiến | API model + embedding, RAG và pgvector; chưa gọi API trả phí |

## Chạy nhanh bằng Docker

Cần Git và Docker Desktop đang chạy **Linux containers**.

```powershell
git clone https://github.com/hoaqhienn/petfood-store-www-java-group10.git
cd petfood-store-www-java-group10
Copy-Item .env.example .env
docker compose --profile app up --build -d
docker compose --profile app ps
```

macOS/Linux: dùng `cp .env.example .env` thay cho `Copy-Item`.

| Dịch vụ | Địa chỉ mặc định |
|---|---|
| Website | http://localhost:5173 |
| Trang quản trị giữ chỗ | http://localhost:5173/admin |
| API giới thiệu | http://localhost:8080/api/v1/info |
| Health check PostgreSQL/Redis | http://localhost:8080/actuator/health |
| Hộp thư Mailpit | http://localhost:8025 |

Lần chạy đầu cần tải image, Gradle và dependencies. Chờ backend healthy trước khi truy cập frontend.

```powershell
docker compose logs -f backend
docker compose --profile app down
```

Lệnh `down` giữ dữ liệu trong volumes. **`down -v` xóa dữ liệu local**; chỉ dùng khi chủ động muốn reset.

## Cấu trúc repo

```text
backend/
  gradle/wrapper/         # Gradle được pin và kiểm tra checksum
  src/main/java/.../petfood/
    account/             # Đăng ký, đăng nhập, /me, worker gửi email outbox
    cart/                # Giỏ hàng trong Redis Session
    catalog/             # Danh mục và sản phẩm
    checkout/            # Xem trước, đặt/hủy đơn COD, lịch sử, quản trị đơn
    config/              # Spring Security, cấu hình chung
    system/              # API giới thiệu
  src/main/resources/
    db/migration/        # Flyway V1–V6: pgvector, catalog, tài khoản/outbox, đơn COD
    db/demo/             # Dữ liệu sản phẩm mẫu (bật qua PETFOOD_FLYWAY_LOCATIONS)
  src/test/              # API security + tích hợp PostgreSQL/Redis
frontend/
  src/app/               # App, routing, CSS chung
  src/components/        # Header, Footer, Sidebar, Hero, thẻ sản phẩm
  src/features/          # catalog/, cart/ (giỏ demo và giỏ theo Session)
  src/shared/            # API client dùng đường dẫn tương đối
  tests/                 # Test API client giỏ hàng (node --test)
docs/                    # Chạy local, kiến trúc, phạm vi, roadmap
.github/                 # CI, mẫu PR và issue
compose.yaml
.env.example
```

## Tài liệu và thành viên

- [Danh mục và đặc tả use case](docs/USE_CASES.md)
- [Use Case Diagram và lộ trình UML](docs/design/README.md)
- [Activity Diagram — 8 luồng P0](docs/design/activities/README.md)

- [Hướng dẫn phát triển local và xử lý lỗi](docs/LOCAL_DEVELOPMENT.md)
- [Kiến trúc và quyết định kỹ thuật](docs/ARCHITECTURE.md)
- [Yêu cầu môn học và phạm vi](docs/REQUIREMENTS.md)
- [Quy tắc nghiệp vụ và quyết định cần review](docs/BUSINESS_RULES.md)
- [Ràng buộc ứng dụng và kết quả rà soát](docs/CONSTRAINTS.md)
- [Mô hình dữ liệu dự kiến](docs/DOMAIN_MODEL.md)
- [Hợp đồng API dự thảo](docs/API_CONTRACT.md)
- [Kịch bản nghiệm thu](docs/ACCEPTANCE.md)
- [Lộ trình và phân công](docs/ROADMAP.md)
- [Quy tắc làm việc nhóm](CONTRIBUTING.md)
- [Kết quả kiểm chứng bộ khung](docs/VERIFICATION.md)

| Thành viên | Phụ trách chính |
|---|---|
| Hoàng Hiện | Backend, CSDL, hạ tầng, tích hợp AI |
| Anh Minh | Frontend khách hàng, quản trị, trải nghiệm người dùng |

Cả hai có thể làm chéo; người còn lại review trước khi merge.

Không commit `.env`, mật khẩu thật, API key, dữ liệu khách hàng hoặc file build. Cấu hình Docker hiện tại chỉ dành cho local, bind các cổng vào `127.0.0.1`.

