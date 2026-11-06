import { useState } from "react";
import type { Product } from "../shared/types";
import "./ProductImage.css";

export function ProductImage({ product }: { product: Product }) {
  const [failed, setFailed] = useState(false);
  if (product.imageUrl && !failed)
    return (
      <img
        className="product-photo"
        src={product.imageUrl}
        alt={product.name}
        loading="lazy"
        onError={() => setFailed(true)}
      />
    );
  if (!product.color)
    return <div className="image-placeholder">Chưa có ảnh sản phẩm</div>;
  return (
    <div
      className={`food-pack ${product.weightGrams < 100 ? "food-pack--pouch" : ""}`}
      style={{ "--pack-color": product.color } as React.CSSProperties}
      aria-label={`Bao bì minh họa ${product.name}`}
      role="img"
    >
      <div className="pack-seal" />
      <span className="pack-brand">
        petfood<span>®</span>
      </span>
      <span className="pack-kitchen">K I T C H E N</span>
      <div className="pack-art">
        <span>{product.species === "DOG" ? "🐕" : "🐈"}</span>
      </div>
      <strong className="pack-flavor">{product.flavor}</strong>
      <span className="pack-recipe">everyday goodness</span>
      <div className="pack-base">
        <span>{product.species === "DOG" ? "DOG FOOD" : "CAT FOOD"}</span>
        <b>
          {product.weightGrams < 1000
            ? `${product.weightGrams}g`
            : `${product.weightGrams / 1000}kg`}
        </b>
      </div>
    </div>
  );
}
