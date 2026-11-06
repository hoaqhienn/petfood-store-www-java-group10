import type { Product } from "../shared/types";
import { useContext } from "react";
import { CartBusyContext } from "../features/cart/useSessionCart";
import { formatPrice, isDemo, shopLink } from "../shared/types";
import { Icon } from "../shared/Icon";
import { ProductImage } from "./ProductImage";
import "./ProductCard.css";

export function ProductCard({
  product,
  onAdd,
}: {
  product: Product;
  onAdd: (product: Product) => void;
}) {
  const cartBusy = useContext(CartBusyContext);
  return (
    <article className="product-card">
      <a
        className="product-card__image"
        href={shopLink("/products/" + product.id)}
      >
        <span className="product-card__badge">
          {product.species === "DOG" ? "CHO CHÓ" : "CHO MÈO"}
        </span>
        <ProductImage product={product} />
      </a>
      <div className="product-card__body">
        <span className="product-card__brand">{product.brand}</span>
        <h3>
          <a href={shopLink("/products/" + product.id)}>{product.name}</a>
        </h3>
        <p className="product-card__weight">
          {product.weightGrams.toLocaleString("vi")} g <span>•</span>{" "}
          {product.availableStock > 0 ? "Còn hàng" : "Hết hàng"}
        </p>
        <div className="product-card__bottom">
          <strong>{formatPrice(product.price)}</strong>
          <button
            className="add-button"
            disabled={cartBusy || product.availableStock < 1}
            onClick={() => onAdd(product)}
            aria-label={`Thêm ${product.name} vào ${isDemo ? "giỏ mẫu" : "giỏ hàng"}`}
            title={isDemo ? "Thêm vào giỏ mẫu" : "Thêm vào giỏ hàng"}
          >
            <Icon name="plus" size={18} />
            <span>{product.availableStock < 1 ? 'Hết hàng' : 'Thêm vào giỏ'}</span>
          </button>
        </div>
      </div>
    </article>
  );
}
