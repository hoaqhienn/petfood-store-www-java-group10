import { useEffect, useRef } from "react";
import { Icon } from "../../shared/Icon";
import { formatPrice } from "../../shared/types";
import { ProductImage } from "../../components/ProductImage";
import type { CartItem } from "./useCart";
import "./CartDrawer.css";

export function CartDrawer({
  items,
  onClose,
  onChange,
}: {
  items: CartItem[];
  onClose: () => void;
  onChange: (id: string, quantity: number) => void;
}) {
  const panel = useRef<HTMLElement>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    panel.current?.focus();
    return () => {
      document.body.style.overflow = previousOverflow;
      previous?.focus();
    };
  }, []);
  return (
    <div className="drawer-backdrop" onClick={onClose}>
      <section
        className="cart-drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="cart-title"
        ref={panel}
        tabIndex={-1}
        onClick={(event) => event.stopPropagation()}
        onKeyDown={(event) => {
          if (event.key === "Escape") onClose();
          if (event.key === "Tab") {
            const controls = panel.current?.querySelectorAll<HTMLElement>(
              "button:not(:disabled), a[href]",
            );
            if (!controls?.length) return;
            const first = controls[0],
              last = controls[controls.length - 1];
            if (
              event.shiftKey &&
              (document.activeElement === first ||
                document.activeElement === panel.current)
            ) {
              event.preventDefault();
              last.focus();
            } else if (
              !event.shiftKey &&
              (document.activeElement === last ||
                document.activeElement === panel.current)
            ) {
              event.preventDefault();
              first.focus();
            }
          }
        }}
      >
        <div className="drawer-title">
          <div>
            <span>MỘT CHÚT YÊU THƯƠNG</span>
            <h2 id="cart-title">Giỏ hàng của bạn</h2>
          </div>
          <button
            className="icon-button"
            aria-label="Đóng giỏ hàng"
            onClick={onClose}
          >
            <Icon name="close" />
          </button>
        </div>
        <p className="cart-demo-note">
          Giỏ mẫu để trải nghiệm giao diện. Chưa tạo đơn hàng hoặc thanh toán.
        </p>
        <div className="cart-items">
          {items.length === 0 ? (
            <div className="empty-cart">
              <Icon name="bag" size={48} />
              <h3>Giỏ hàng còn trống</h3>
              <p>Chọn một món ngon cho bạn nhỏ nhé.</p>
              <button className="button" onClick={onClose}>
                Tiếp tục khám phá <Icon name="arrow" size={17} />
              </button>
            </div>
          ) : (
            items.map(({ product, quantity }) => (
              <article className="cart-item" key={product.id}>
                <div className="cart-item-image">
                  <ProductImage product={product} />
                </div>
                <div className="cart-item-info">
                  <h3>{product.name}</h3>
                  <strong>{formatPrice(product.price)}</strong>
                  <div className="cart-item-actions">
                    <div className="quantity">
                      <button
                        aria-label={`Giảm số lượng ${product.name}`}
                        onClick={() => onChange(product.id, quantity - 1)}
                      >
                        <Icon name="minus" size={13} />
                      </button>
                      <span>{quantity}</span>
                      <button
                        disabled={quantity >= product.availableStock}
                        aria-label={`Tăng số lượng ${product.name}`}
                        onClick={() => onChange(product.id, quantity + 1)}
                      >
                        <Icon name="plus" size={13} />
                      </button>
                    </div>
                    <button
                      className="remove-item"
                      onClick={() => onChange(product.id, 0)}
                    >
                      Xóa
                    </button>
                  </div>
                </div>
              </article>
            ))
          )}
        </div>
        {items.length > 0 && (
          <div className="cart-total">
            <p>
              <span>Tạm tính</span>
              <strong>
                {formatPrice(
                  items.reduce(
                    (sum, item) =>
                      sum + Number(item.product.price) * item.quantity,
                    0,
                  ),
                )}
              </strong>
            </p>
            <button className="button" onClick={onClose}>
              Tiếp tục chọn món <Icon name="arrow" size={17} />
            </button>
            <small>
              Chức năng đặt hàng sẽ được nối với Spring Boot ở bước sau.
            </small>
          </div>
        )}
      </section>
    </div>
  );
}
