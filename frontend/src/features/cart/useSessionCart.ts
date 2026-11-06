import { createContext, useEffect, useRef, useState } from "react";
import { SessionCartClient, type SessionCart } from "./cartApi";
import type { Product } from "../../shared/types";

export const CartBusyContext = createContext(false);

export function useSessionCart(enabled: boolean) {
  const client = useRef(new SessionCartClient());
  const locked = useRef(false);
  const [cart, setCart] = useState<SessionCart | null>(null);
  const [busy, setBusy] = useState(enabled);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  function sync() {
    setCart(client.current.cart);
    setReady(client.current.ready);
  }

  useEffect(() => {
    if (!enabled) return;
    let active = true;
    client.current
      .refresh()
      .catch((e: Error) => {
        if (active) setError(e.message || "Không tải được giỏ hàng.");
      })
      .finally(() => {
        if (active) {
          sync();
          setBusy(false);
        }
      });
    return () => {
      active = false;
    };
  }, [enabled]);

  async function reload() {
    if (locked.current || busy) return;
    locked.current = true;
    setBusy(true);
    setError("");
    try {
      await client.current.refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Không tải được giỏ hàng.");
    } finally {
      sync();
      setBusy(false);
      locked.current = false;
    }
  }

  async function change(
    method: "POST" | "PATCH" | "DELETE",
    id: string,
    quantity?: number,
  ) {
    if (locked.current || busy) return;
    if (
      method !== "DELETE" &&
      (!Number.isInteger(quantity) || quantity! < 1 || quantity! > 99)
    ) {
      setError("Số lượng phải là số nguyên từ 1 đến 99.");
      return;
    }
    locked.current = true;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await client.current.mutate(method, id, quantity);
      setMessage(
        method === "POST"
          ? "Đã thêm sản phẩm vào giỏ hàng."
          : "Đã cập nhật giỏ hàng.",
      );
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Không cập nhật được giỏ hàng.",
      );
    } finally {
      sync();
      setBusy(false);
      locked.current = false;
    }
  }

  return {
    cart,
    busy,
    ready,
    error,
    message,
    reload,
    add: (product: Product) => change("POST", product.id, 1),
    update: (id: string, quantity: number) => change("PATCH", id, quantity),
    remove: (id: string) => change("DELETE", id),
    clearMessage: () => setMessage(""),
    count: cart?.items.reduce((sum, item) => sum + item.quantity, 0) || 0,
  };
}
