import type { Product, ProductPage } from "../../shared/types";

async function request<T>(path: string, signal: AbortSignal): Promise<T> {
  const response = await fetch("/api/v1" + path, {
    signal,
    credentials: "same-origin",
  });
  if (!response.ok)
    throw new Error(
      response.status === 404
        ? "Không tìm thấy sản phẩm."
        : "Chưa kết nối được danh mục. Hãy kiểm tra Spring Boot ở cổng 8080.",
    );
  return response.json();
}
export const getProducts = (query: string, signal: AbortSignal) =>
  request<ProductPage>("/products?" + query, signal);
export const getProduct = (id: string, signal: AbortSignal) =>
  request<Product>("/products/" + encodeURIComponent(id), signal);
