# Giao diện PetFood

## Chạy frontend

Mở terminal trong thư mục `D:\petfood-store-www-java-group10-main\frontend`:

```powershell
npm.cmd run dev
```

Mở http://127.0.0.1:5173. Nếu cổng đã có tiến trình đang chạy, dùng trang hiện có hoặc `npm.cmd run dev -- --port 5175`.

`npm.cmd run build` kiểm tra TypeScript và tạo bản build. Lệnh build không còn phụ thuộc vào pnpm trong PATH. Cài dependency mới vẫn theo pnpm và lockfile của dự án.

## Tổ chức mã nguồn

- `src/app/App.tsx`: ghép layout, chọn trang và hiển thị thông báo.
- `src/app/styles.css`: màu dùng chung, reset và responsive nền tảng.
- `src/components/`: Header, Sidebar, Hero, Footer, ProductCard, ProductImage; mỗi thành phần có CSS riêng.
- `src/features/catalog/`: trang danh sách, chi tiết, hook tải dữ liệu và client API tách biệt.
- `src/features/cart/`: `cartApi.ts` xử lý API Session/CSRF; `useSessionCart.ts` quản lý trạng thái; `CartPage.tsx` và CSS cho trang `/cart`. Giỏ mẫu và drawer tách riêng.
- `src/shared/`: kiểu dữ liệu, định dạng tiền, link có chế độ nguồn dữ liệu, icon dùng lại.
- `src/data/demoProducts.ts`: dữ liệu minh họa, không trộn với JSX.

Thẻ sản phẩm được render bằng `map` từ dữ liệu, không sao chép HTML từng sản phẩm. Danh sách ở trang chủ và trang sản phẩm dùng chung `ProductListing`.

## Hai chế độ

- Mặc định dùng API: danh mục và chi tiết từ `/api/v1/products`; giỏ tại `/cart` gọi API Session theo `docs/CART_FRONTEND_HANDOFF.md`. Proxy Vite giữ nguyên sang Spring Boot cổng 8080. Không tự thay bằng dữ liệu mẫu khi API lỗi.
- Chỉ khi mở `?source=demo` mới dùng 8 sản phẩm minh họa và giỏ localStorage. Không trộn giỏ mẫu với giỏ Session.
- Giỏ API khởi tạo GET cart rồi GET CSRF tuần tự; POST/PATCH gửi expectedRevision, DELETE xong đọc lại giỏ; tổng tiền lấy từ subtotal server. Lỗi 409/403 hoặc mất phản hồi yêu cầu đọc lại và người dùng chủ động thao tác tiếp, không tự gửi lại POST.
- Đặt hàng, đăng nhập, thanh toán và quản trị chưa triển khai trong lần sửa UI này.
- Banner chuyển bằng nút, không tự chạy. Hiệu ứng xuất hiện, hover và drawer tôn trọng `prefers-reduced-motion`.

## Demo khi thuyết trình

1. Trang chủ: giới thiệu tìm kiếm, danh mục, banner hai trạng thái.
2. Bấm **Cho mèo**, chọn sắp xếp giá thấp đến cao.
3. Tìm “cá hồi”, mở chi tiết sản phẩm.
4. Khi backend đã chạy: thêm món còn hàng, mở `/cart`, sửa số lượng, xóa, tải lại để kiểm tra giỏ Session. Mở hai tab để thử revision cũ. Với `source=demo`, nói rõ giỏ localStorage chỉ minh họa UI.
5. Thu nhỏ cửa sổ: menu cuộn ngang, lưới sản phẩm hai cột.
6. Chuyển sang API: giải thích frontend gọi backend; nếu Spring Boot chưa chạy sẽ hiện trạng thái lỗi có nút thử lại.

## Câu hỏi giáo viên có thể hỏi

- **Vì sao tách component?** Để tái sử dụng Header, ProductCard và các khối UI, giảm trùng lặp và sửa giao diện tại một nơi.
- **State và props dùng ở đâu?** Hook `useSessionCart` giữ trạng thái được API xác nhận; App truyền số lượng cho Header và callback `onAdd` cho ProductListing/ProductCard. Context khóa nút thêm khi đang gửi hoặc chưa khởi tạo được giỏ.
- **useEffect làm gì?** Tải danh mục khi tham số truy vấn thay đổi, hủy request khi rời trang; quản lý timeout và focus của drawer.
- **Dữ liệu là thật chưa?** Mặc định gọi Spring Boot. Backend cần PostgreSQL và Redis theo hướng dẫn local. Chế độ `source=demo` riêng mới dùng dữ liệu giả.
- **localStorage có bảo đảm giá và tồn kho không?** Không. Nó chỉ phục vụ demo UI. Khi đặt hàng thật, backend phải xác thực sản phẩm, giá, tồn kho và quyền truy cập.
- **Responsive/animation làm thế nào?** CSS Grid, media query, transition và keyframes; có chế độ giảm chuyển động.
- **Tại sao chưa dùng React Router?** Phạm vi hiện tại chỉ vài đường dẫn, dùng URL và tải lại trang để giữ đơn giản. Có thể chuyển sang router khi thêm nhiều trang.

## Kiểm chứng tích hợp frontend

Chạy `npm.cmd test` để kiểm tra client với phản hồi API giả lập: khởi tạo tuần tự, revision, CSRF, DELETE đọc lại, 403/409/422/503, mất phản hồi và khóa thao tác ghi đồng thời. Đây không thay thế kiểm thử với Spring Boot/Redis thật. Chạy `npm.cmd run build` để kiểm tra TypeScript và build.

Tại lần chỉnh sửa này Docker Engine truy cập được nhưng chưa có service PetFood chạy; chưa xác nhận nghiệm thu toàn stack, hai tab thật hoặc cửa sổ riêng tư. Không đánh dấu các checklist đó là hoàn thành.

## Nguồn hình ảnh minh họa

Ảnh Golden Retriever: Faber Leonardo / Unsplash, https://unsplash.com/photos/a-close-up-of-a-dog-with-a-white-background-EVJZJ7_6CLY (tệp `public/images/golden-retriever.jpg`). Bao bì PetFood Kitchen là minh họa tự dựng, không phải ảnh hàng thương mại.
