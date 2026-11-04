# PetFood

**Good food. Happy pets.** Website giới thiệu và bán thức ăn chó mèo, ưu tiên tối giản, nhanh và tiện lợi.

Đồ án **Lập trình WWW (Java)** · Lớp **DHKTPM19ATT** · **Nhóm 10** · **Đề tài 42**.
Thời gian dự kiến: **01–30/11/2026**.

## Trạng thái hiện tại

Đây là **bộ khung khởi đầu**, chưa phải website bán hàng hoàn chỉnh.

- Có: Spring Boot + Gradle Wrapper, React + TypeScript, trang giới thiệu và trang giữ chỗ `/admin`.
- Có: API giới thiệu, health check, Spring Security chặn API chưa triển khai.
- Có: PostgreSQL + pgvector, Redis Session, Flyway, cấu hình SMTP/Mailpit, Docker Compose.
- Có: kiểm thử API, kiểm thử tích hợp hạ tầng và GitHub Actions.
- Chưa có: đăng ký/đăng nhập, CRUD nghiệp vụ, giỏ hàng, checkout, email nghiệp vụ, AI và tracking.

**Phân tích cập nhật 03/11/2026:** đã bổ sung danh mục 15 chức năng, ma trận quyền, quy tắc đơn COD/tồn kho, API dự thảo, mô hình dữ liệu và 30 kịch bản nghiệm thu. Đây là tài liệu thiết kế, chưa triển khai nghiệp vụ; các quyết định D01–D08 được đánh dấu đề xuất để review.

## Công nghệ

Rà soát bổ sung: [CONSTRAINTS.md](docs/CONSTRAINTS.md) ánh xạ RB01–RB22 sang nơi thực thi và kịch bản kiểm thử; danh sách nghiệm thu hiện tăng từ 30 lên **36**. Những ràng buộc nghiệp vụ này chưa được triển khai trong mã chạy.

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
    config/              # Spring Security, cấu hình chung
    system/              # API giới thiệu
  src/main/resources/
    db/migration/        # Flyway; V1 bật extension vector
  src/test/              # API security + tích hợp PostgreSQL/Redis
frontend/
  src/app/               # App, trang giới thiệu, CSS
  src/shared/            # API client dùng đường dẫn tương đối
  src/features/          # Vị trí mở rộng module nghiệp vụ
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

