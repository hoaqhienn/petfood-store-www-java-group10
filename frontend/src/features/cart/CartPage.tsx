import { useEffect, useState } from "react";
import type { SessionCartItem } from "./cartApi";
import type { useSessionCart } from "./useSessionCart";
import { formatPrice, shopLink } from "../../shared/types";
import "./CartPage.css";

function CartRow({
  item,
  disabled,
  update,
  remove,
}: {
  item: SessionCartItem;
  disabled: boolean;
  update: (id: string, quantity: number) => void;
  remove: (id: string) => void;
}) {
  const [quantity, setQuantity] = useState(String(item.quantity));
  useEffect(() => {
    setQuantity(String(item.quantity));
  }, [item]);
  const unavailable = item.unitPrice === null || item.name === null;
  const value = Number(quantity);
  const valid =
    quantity.trim() !== "" &&
    Number.isInteger(value) &&
    value >= 1 &&
    value <= 99;
  return (
    <article className="session-cart-row">
      <div>
        <h2>
          {unavailable ? (
            "Sản phẩm không còn bán"
          ) : (
            <a href={shopLink("/products/" + item.productId)}>{item.name}</a>
          )}
        </h2>
        <p>
          {item.unitPrice === null
            ? "Không có giá hiện hành"
            : formatPrice(item.unitPrice)}
        </p>
        {item.availabilityError && (
          <p className="cart-row-error">
            {item.availabilityError === "INSUFFICIENT_STOCK"
              ? `Không đủ tồn kho (còn ${item.availableStock ?? 0}). Hãy giảm số lượng hoặc xóa món.`
              : "Sản phẩm không còn bán. Bạn vẫn có thể xóa khỏi giỏ."}
          </p>
        )}
      </div>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          if (valid) update(item.productId, value);
        }}
      >
        <label>
          Số lượng
          <input
            aria-label={`Số lượng ${item.name || item.productId}`}
            type="number"
            min="1"
            max="99"
            step="1"
            value={quantity}
            disabled={disabled || unavailable}
            onChange={(event) => setQuantity(event.target.value)}
          />
        </label>
        <button
          className="button"
          disabled={
            disabled || unavailable || !valid || value === item.quantity
          }
        >
          Cập nhật
        </button>
        {!valid && <small role="alert">Nhập số nguyên từ 1 đến 99.</small>}
      </form>
      <button
        className="text-button"
        disabled={disabled}
        onClick={() => remove(item.productId)}
      >
        Xóa
      </button>
    </article>
  );
}

export function CartPage({
  state,
}: {
  state: ReturnType<typeof useSessionCart>;
}) {
  return (
    <section className="session-cart">
      <span className="eyebrow">MÓN NGON CHO BẠN NHỎ</span>
      <h1>Giỏ hàng của bạn</h1>
      <p>Giỏ hàng được lưu theo phiên của trình duyệt.</p>
      {state.busy && <p role="status">Đang tải hoặc lưu giỏ hàng…</p>}
      {state.error && (
        <p className="notice" role="alert">
          {state.error}
        </p>
      )}
      <button
        className="text-button"
        disabled={state.busy}
        onClick={() => void state.reload()}
      >
        Tải lại giỏ hàng
      </button>
      {state.cart && (
        <>
          {state.cart.items.length === 0 ? (
            <div className="catalog-state">
              <h2>Giỏ hàng còn trống</h2>
              <a className="button" href={shopLink("/products")}>
                Chọn món cho bé
              </a>
            </div>
          ) : (
            <>
              <div aria-busy={state.busy}>
                {state.cart.items.map((item) => (
                  <CartRow
                    key={item.productId}
                    item={item}
                    disabled={state.busy || !state.ready}
                    update={state.update}
                    remove={state.remove}
                  />
                ))}
              </div>
              <div className="session-cart-total">
                <span>Tạm tính</span>
                <strong>{formatPrice(state.cart.subtotal)}</strong>
              </div>
              <p className="notice">Chức năng đặt hàng chưa được triển khai.</p>
            </>
          )}
        </>
      )}
    </section>
  );
}
