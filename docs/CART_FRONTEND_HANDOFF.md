# Bàn giao giao diện giỏ hàng cho Minh Anh

Mục tiêu demo: khách chưa đăng nhập thêm món từ danh sách hoặc chi tiết, mở `/cart`, sửa số lượng, xóa món và tải lại trang vẫn thấy giỏ của phiên đó. Phát triển dựa trên nhánh `feat/session-cart` khi [PR #10](https://github.com/hoaqhienn/petfood-store-www-java-group10/pull/10) còn mở. Sau khi PR vào `main`, dùng `main` làm nhánh gốc.

## Giao diện cần làm

- **Danh sách và chi tiết:** thêm nút **Thêm vào giỏ** cho sản phẩm còn hàng. Khi hết hàng, hiển thị trạng thái và vô hiệu hóa nút. Báo kết quả sau khi API phản hồi.
- **Điều hướng:** thêm liên kết **Giỏ hàng** tới `/cart`.
- **Trang giỏ:** hiển thị tên, đơn giá, số lượng và tạm tính do server trả. Cho sửa số lượng nguyên từ 1 đến 99 và xóa từng dòng. Có trạng thái đang tải, rỗng, lỗi và đang lưu. Giữ nút xóa cho dòng không còn bán hoặc thiếu tồn.

## Tích hợp API

Dùng tiền tố `/api/v1` qua proxy cùng origin và giữ cookie phiên. Trước thao tác đầu tiên, gọi `GET /cart` rồi `GET /auth/csrf` **tuần tự** để dùng cùng phiên. Khi gửi yêu cầu ghi, thêm header có tên `headerName` và giá trị `token` do endpoint CSRF trả.

| Thao tác | Yêu cầu | Cập nhật giao diện |
|---|---|---|
| Xem giỏ | `GET /cart` | Dùng `cartToken`, `revision`, `items` và `subtotal` từ response. |
| Thêm một món | `POST /cart/items` với `{"productId":"1","quantity":1,"expectedRevision":0}` | Thay giỏ đang hiển thị bằng response. POST cộng thêm vào số lượng hiện có. |
| Đổi số lượng | `PATCH /cart/items/{productId}` với `{"quantity":3,"expectedRevision":1}` | Thay giỏ bằng response. PATCH đặt số lượng tuyệt đối. |
| Xóa món | `DELETE /cart/items/{productId}?expectedRevision=2` | Sau `204`, gọi lại GET để lấy giỏ và revision mới. |

Các ID và revision trong ví dụ chỉ để minh họa. Gửi revision từ giỏ gần nhất mà giao diện đã đọc. Giữ một thao tác ghi đang chờ tại một thời điểm trên trang. Dùng giá VND dạng chuỗi để hiển thị, lấy tạm tính từ `subtotal`. Với dòng có `name` hoặc `unitPrice` null, dùng nhãn “Sản phẩm không còn bán” và giữ nút xóa.

- **409 CART_CHANGED:** tải lại giỏ, thông báo giỏ đã đổi và để khách xác nhận thao tác mới. Không tự gửi lại POST.
- **422:** hiển thị lỗi số lượng, tồn kho, sản phẩm ngừng bán hoặc giới hạn giỏ. Giữ dữ liệu giỏ đã xác nhận.
- **403:** tải lại giỏ và CSRF trước khi khách thử lại. Nếu mất phản hồi hoặc gặp lỗi mạng/503, đọc lại giỏ trước khi thử thêm lần nữa để tránh cộng trùng.

Chi tiết response và quy ước lỗi nằm trong [API contract](API_CONTRACT.md).

## Checklist nghiệm thu

Chạy frontend và backend với Redis, PostgreSQL và dữ liệu demo theo [hướng dẫn local](LOCAL_DEVELOPMENT.md).

- [ ] Thêm từ danh sách và từ chi tiết. Thêm cùng SKU hai lần cộng đúng số lượng.
- [ ] Sửa số lượng, xóa dòng và tải lại trang. Số lượng và tạm tính khớp response.
- [ ] Sản phẩm hết hàng không thể thêm. Số lượng 0, lẻ hoặc lớn hơn tồn hiển thị lỗi phù hợp.
- [ ] Mở cùng giỏ ở hai tab. Sau khi tab A cập nhật, thao tác dựa trên revision cũ ở tab B hiện thông báo và tải lại giỏ.
- [ ] Mở cửa sổ riêng tư và thấy giỏ riêng.
- [ ] Chạy `pnpm build` trong frontend và ghi kết quả kiểm tra vào PR giao diện.
