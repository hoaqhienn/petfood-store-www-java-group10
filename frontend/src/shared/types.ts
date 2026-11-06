export interface Product {
  id: string;
  name: string;
  description: string;
  brand: string;
  categoryName: string;
  species: string;
  lifeStage: string;
  weightGrams: number;
  price: string;
  availableStock: number;
  imageUrl?: string;
  color?: string;
  flavor?: string;
}

export interface ProductPage {
  items: Product[];
  page: number;
  totalPages: number;
  totalElements: number;
}

export const isDemo =
  new URLSearchParams(window.location.search).get("source") === "demo";
export function shopLink(
  path = "/products",
  params: Record<string, string> = {},
) {
  return (
    path +
    "?" +
    new URLSearchParams({ source: isDemo ? "demo" : "api", ...params })
  );
}
export function formatPrice(price: string | number) {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(typeof price === "string" ? BigInt(price) : price);
}
