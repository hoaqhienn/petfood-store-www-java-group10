export interface SessionCartItem {
  productId: string;
  quantity: number;
  name: string | null;
  unitPrice: string | null;
  availableStock: number | null;
  availabilityError: string | null;
}

export interface SessionCart {
  cartToken: string;
  revision: number;
  items: SessionCartItem[];
  subtotal: string;
}

interface CsrfToken {
  token: string;
  headerName: string;
}

export class CartApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message);
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 8000);
  try {
    const response = await fetch("/api/v1" + path, {
      ...init,
      credentials: "same-origin",
      signal: controller.signal,
    });
    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new CartApiError(
        response.status,
        error.code || "API_ERROR",
        error.message || "Chưa kết nối được giỏ hàng. Vui lòng thử tải lại.",
      );
    }
    return response.status === 204 ? (undefined as T) : await response.json();
  } finally {
    clearTimeout(timeout);
  }
}

// Một client cho mỗi trang. Không đọc/ghi giỏ nghiệp vụ trong localStorage.
export class SessionCartClient {
  cart: SessionCart | null = null;
  ready = false;
  private csrf: CsrfToken | null = null;
  private loading: Promise<void> | null = null;
  private writing = false;

  refresh(): Promise<void> {
    if (this.loading) return this.loading;
    this.ready = false;
    this.loading = (async () => {
      // Bắt buộc tuần tự để CSRF dùng cùng cookie Session vừa khởi tạo.
      this.cart = await request<SessionCart>("/cart");
      this.csrf = await request<CsrfToken>("/auth/csrf");
      this.ready = true;
    })().finally(() => {
      this.loading = null;
    });
    return this.loading;
  }

  async mutate(
    method: "POST" | "PATCH" | "DELETE",
    productId: string,
    quantity?: number,
  ): Promise<void> {
    if (this.writing)
      throw new Error("Một thao tác đang được lưu. Vui lòng chờ.");
    this.writing = true;
    try {
      if (!this.ready || !this.cart || !this.csrf) {
        throw new Error("Hãy tải lại giỏ hàng trước khi tiếp tục.");
      }
      const revision = this.cart.revision;
      const path =
        method === "POST"
          ? "/cart/items"
          : "/cart/items/" + encodeURIComponent(productId);
      const body =
        method === "POST"
          ? { productId, quantity, expectedRevision: revision }
          : { quantity, expectedRevision: revision };
      try {
        const result = await request<SessionCart>(
          path + (method === "DELETE" ? `?expectedRevision=${revision}` : ""),
          {
            method,
            headers: {
              "Content-Type": "application/json",
              [this.csrf.headerName]: this.csrf.token,
            },
            ...(method === "DELETE" ? {} : { body: JSON.stringify(body) }),
          },
        );
        if (method === "DELETE")
          this.cart = await request<SessionCart>("/cart");
        else this.cart = result;
      } catch (error) {
        if (error instanceof CartApiError && error.status === 422) throw error;
        // Không gửi lại thao tác ghi: phản hồi mất có thể đã được server xử lý.
        const reason =
          error instanceof CartApiError && error.status === 409
            ? "Giỏ đã thay đổi ở nơi khác."
            : error instanceof CartApiError && error.status === 403
              ? "Phiên hoặc mã bảo vệ đã thay đổi."
              : "Chưa xác nhận được kết quả thao tác.";
        try {
          await this.refresh();
        } catch {
          throw new Error(
            reason +
              " Không tải lại được giỏ; hãy bấm Tải lại trước khi thao tác tiếp.",
          );
        }
        throw new Error(
          reason +
            " Đã tải lại giỏ. Hãy kiểm tra và chủ động thực hiện lại nếu cần.",
        );
      }
    } finally {
      this.writing = false;
    }
  }
}
