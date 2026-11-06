import { useState } from "react";
import type { Product } from "../../shared/types";
import { demoProducts } from "../../data/demoProducts";

export interface CartItem {
  product: Product;
  quantity: number;
}
const key = "petfood-ui-demo-cart-v1";
function readCart(): CartItem[] {
  try {
    const saved: unknown = JSON.parse(localStorage.getItem(key) || "[]");
    if (!Array.isArray(saved)) return [];
    return demoProducts.flatMap((product) => {
      const item = saved.find(
        (row) =>
          row &&
          row.id === product.id &&
          Number.isInteger(row.quantity) &&
          row.quantity > 0,
      );
      return item
        ? [
            {
              product,
              quantity: Math.min(item.quantity, product.availableStock),
            },
          ]
        : [];
    });
  } catch {
    return [];
  }
}
export function useCart() {
  const [items, setItems] = useState<CartItem[]>(readCart);
  const [message, setMessage] = useState("");
  function update(next: CartItem[]) {
    setItems(next);
    try {
      localStorage.setItem(
        key,
        JSON.stringify(
          next.map((item) => ({
            id: item.product.id,
            quantity: item.quantity,
          })),
        ),
      );
    } catch {
      /* Giỏ vẫn dùng được trong phiên nếu trình duyệt chặn lưu trữ. */
    }
  }
  function add(product: Product) {
    const found = items.find((item) => item.product.id === product.id);
    if (found && found.quantity >= product.availableStock) {
      setMessage("Đã đạt số lượng tồn kho mẫu.");
      return;
    }
    update(
      found
        ? items.map((item) =>
            item === found ? { ...item, quantity: item.quantity + 1 } : item,
          )
        : [...items, { product, quantity: 1 }],
    );
    setMessage(`Đã thêm ${product.name} vào giỏ mẫu.`);
  }
  function change(id: string, quantity: number) {
    update(
      items
        .map((item) =>
          item.product.id === id
            ? {
                ...item,
                quantity: Math.max(
                  0,
                  Math.min(quantity, item.product.availableStock),
                ),
              }
            : item,
        )
        .filter((item) => item.quantity > 0),
    );
  }
  return {
    items,
    add,
    change,
    message,
    clearMessage: () => setMessage(""),
    count: items.reduce((sum, item) => sum + item.quantity, 0),
  };
}
