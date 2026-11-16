# Phát triển local

## 1. Công cụ

- Git; Docker Desktop chạy Linux containers.
- JDK **17** và `JAVA_HOME` đúng nếu chạy backend ngoài Docker.
- Node.js **22.12+**, pnpm **11.25.0** nếu chạy frontend ngoài Docker.
- Có thể dùng IntelliJ IDEA cho backend và VS Code cho frontend.

```powershell
java -version
node --version
docker version
npm install -g pnpm@11.25.0
```

Không cần cài Gradle hệ thống. Dùng `gradlew.bat` trên Windows và `./gradlew` trên macOS/Linux.

## 2. Chỉ chạy hạ tầng trong Docker

Tại thư mục repo:

```powershell
Copy-Item .env.example .env
docker compose up -d postgres redis mailpit
docker compose ps
```

Mặc định: PostgreSQL `localhost:5432`, DB/user `petfood`, mật khẩu local `petfood_local_only`; Redis `localhost:6379`; SMTP `localhost:1025`.

Terminal backend:

```powershell
cd backend
.\gradlew.bat bootRun
```

Terminal frontend:

```powershell
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

Mở http://localhost:5173. Vite proxy `/api` sang backend ở `localhost:8080`, tránh phải mở CORS rộng. Giữ cùng hostname `localhost` khi thử Session.

**Spring Boot không tự đọc file `.env` ở repo root.** `.env` dùng để Docker Compose nội suy cấu hình. Khi chạy trong IDE/terminal, cấu hình biến môi trường riêng nếu thay đổi giá trị mặc định:

```powershell
$env:DB_URL='jdbc:postgresql://localhost:5432/petfood'
$env:DB_USERNAME='petfood'
$env:DB_PASSWORD='your-local-password'
$env:REDIS_HOST='localhost'
$env:REDIS_PORT='6379'
.\gradlew.bat bootRun
```

Nếu đổi cổng backend khi phát triển, cập nhật proxy trong `frontend/vite.config.ts`. Khi đổi cổng PostgreSQL/Redis trong `.env`, cũng đổi `DB_URL`/`REDIS_PORT` cho backend chạy ngoài Docker. Full Docker tự dùng cổng nội bộ cố định.

## 3. Chạy toàn bộ trong Docker

```powershell
docker compose --profile app up --build -d
docker compose --profile app ps
```

Frontend được build thành static files, Nginx proxy API sang `backend:8080`. Chế độ này không hot reload; sau thay đổi mã nguồn chạy lại với `--build`. Không chạy cùng lúc với dev server trên cùng cổng.

## 4. Kiểm thử

```powershell
# Không cần PostgreSQL/Redis cho các kiểm thử API slice
cd backend
.\gradlew.bat test bootJar

# Cần hạ tầng ở bước 2; dùng DB local riêng cho kiểm thử nếu có dữ liệu thật
.\gradlew.bat test -PintegrationTests --rerun-tasks

cd ../frontend
pnpm build
```

`-PintegrationTests` bổ sung kiểm tra Flyway/pgvector và Session đọc/ghi Redis. CI dùng PostgreSQL/Redis riêng, không kết nối máy cá nhân. `--rerun-tasks` bảo đảm không bỏ qua test do Gradle coi đầu ra trước đó còn mới.

## 5. Email local và email thật

Mailpit nhận email SMTP tại `localhost:1025`, xem tại http://localhost:8025; không gửi ra hộp thư Internet. Bộ khung chỉ cấu hình mail sender, **chưa gửi email đăng ký hoặc đơn hàng**.

Khi triển khai gửi mail thật, set `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH=true`, `MAIL_STARTTLS=true`, `MAIL_FROM` theo nhà cung cấp. Với SMTP port 587 thường dùng STARTTLS. Nhà cung cấp yêu cầu SMTPS 465 cần cấu hình SSL tương ứng, không chỉ đổi port. Không đưa mật khẩu vào commit hoặc ảnh chụp màn hình.

Compose chuyển các biến này cho backend; backend chạy ngoài Docker nhận qua environment của IDE/terminal. Gói ChatGPT/Gemini dùng giao diện không được coi là thông tin xác thực API; AI sẽ có bước cấu hình riêng.

## 6. Lỗi thường gặp

| Triệu chứng | Cách kiểm tra |
|---|---|
| Không kết nối Docker Engine | Mở Docker Desktop, chờ Engine running; kiểm tra `docker version` |
| Port đã được dùng | Dừng tiến trình cũ hoặc đổi port trong `.env`; xem lưu ý proxy ở trên |
| PostgreSQL báo sai mật khẩu sau khi đổi `.env` | Volume cũ vẫn giữ thông tin ban đầu; đổi mật khẩu trong DB hoặc chủ động reset volume nếu dữ liệu có thể bỏ |
| `vector` extension không tồn tại | Phải dùng image `pgvector/pgvector`, không dùng image PostgreSQL thuần |
| Backend không healthy | Xem `docker compose logs backend postgres redis`; cần DB và Redis sẵn sàng |
| Không thấy email | Hiện chưa có luồng gửi email; khi thêm luồng, kiểm tra SMTP host/port |
| Frontend hiện “API chưa sẵn sàng” | Kiểm tra backend và `/api/v1/info`; frontend vẫn hiển thị trang giới thiệu khi API lỗi |
| Java không đúng phiên bản | Chọn JDK 17 trong `JAVA_HOME` và Gradle JVM của IDE |

## 7. Dữ liệu và migration

Không sửa migration đã chạy chung trong nhóm. Thêm `V2__...sql`, `V3__...sql` để tiến hóa schema. Hibernate `ddl-auto=validate` giúp phát hiện lệch schema khi có entity; không dùng `update` để tự ý thay schema.

Không tự viết database function, CHECK constraint hoặc stored procedure theo yêu cầu môn học. Validation nghiệp vụ ở Java, frontend hỗ trợ thông báo; PK/FK/UNIQUE bảo vệ toàn vẹn dữ liệu.

## 8. Chạy catalog với dữ liệu demo

Trong PowerShell tại thư mục gốc repo:

```powershell
$env:PETFOOD_FLYWAY_LOCATIONS = 'classpath:db/migration,classpath:db/demo'
docker compose --profile app up --build -d --wait
```

Mở `http://localhost:5173/products`. Demo có một sản phẩm còn hàng và một sản phẩm hết hàng hiển thị công khai. Một sản phẩm ẩn và một sản phẩm thuộc danh mục ẩn được tạo để kiểm tra API không công khai chúng. Chỉ bật seed trên cơ sở dữ liệu demo. Giữ cấu hình Flyway này khi chạy lại cùng cơ sở dữ liệu demo.

## 9. Tài khoản quản trị đầu tiên

Đăng ký công khai luôn tạo CUSTOMER. Để có ADMIN, đặt hai biến trong `.env` (không commit file này) rồi khởi động backend:

```dotenv
PETFOOD_ADMIN_EMAIL=admin@petfood.local
PETFOOD_ADMIN_PASSWORD=<mật khẩu 8–64 ký tự do bạn tự chọn>
```

Khi khởi động, backend tạo một tài khoản ADMIN ACTIVE nếu email chưa tồn tại. Chạy lại không tạo trùng và không đổi mật khẩu. Nếu email đã thuộc một tài khoản khác, tài khoản đó được giữ nguyên. Để trống email thì bỏ qua bước này. Cấu hình sai (email không hợp lệ, mật khẩu ngoài 8–64 ký tự) làm backend dừng khởi động để tránh tưởng đã có admin.

## 10. Bật trợ lý AI (Gemini)

Mặc định AI tắt: tìm kiếm và trợ lý vẫn chạy bằng tìm theo từ khóa, không gọi API nào. Để bật, tạo API key trong Google AI Studio rồi đặt trong `.env` (không commit):

```dotenv
AI_ENABLED=true
AI_API_KEY=<key của bạn>
```

- Model mặc định: chat `gemini-3.8-flash`, embedding `gemini-embedding-001` rút gọn còn 768 chiều (cột `vector(768)` trong V9). Đổi `AI_EMBEDDING_MODEL` thì vector cũ không bị trộn vì mỗi model có bản ghi riêng, nhưng số chiều phải giữ 768.
- Sau khi bật, job nền tạo embedding cho sản phẩm ACTIVE mỗi phút, 20 sản phẩm một lượt. Sản phẩm vừa sửa nội dung chỉ xuất hiện trong tìm kiếm ngữ nghĩa sau khi job chạy lại.
- Mỗi Session tối đa `AI_QUOTA_PER_MINUTE` (mặc định 10) lượt tìm/hỏi mỗi phút. Google có thể tính phí khi vượt mức miễn phí; đặt giới hạn ngân sách trong tài khoản Google.
- Key chỉ nằm ở backend và chỉ gửi trong header `x-goog-api-key`. Không gửi email, địa chỉ, tên thú cưng hay mật khẩu cho model.
