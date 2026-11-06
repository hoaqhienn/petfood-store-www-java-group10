import { useContext, useEffect, useState } from "react";
import { CartBusyContext } from "../cart/useSessionCart";
import { demoProducts } from "../../data/demoProducts";
import { ProductImage } from "../../components/ProductImage";
import {
  formatPrice,
  isDemo,
  shopLink,
  type Product,
} from "../../shared/types";
import { Icon } from "../../shared/Icon";
import { getProduct } from "./catalogApi";
import "./Catalog.css";

export function ProductDetail({
  id,
  onAdd,
}: {
  id: string;
  onAdd: (product: Product) => void;
}) {
  const [product, setProduct] = useState<Product | null>(null);
  const cartBusy = useContext(CartBusyContext);
  const [error, setError] = useState("");
  useEffect(() => {
    setError("");
    setProduct(null);
    if (isDemo) {
      const found = demoProducts.find((item) => item.id === id);
      if (found) setProduct(found);
      else setError("Không tìm thấy sản phẩm mẫu này.");
      return;
    }
    const controller = new AbortController();
    let active = true;
    const timeout = window.setTimeout(() => controller.abort(), 8000);
    getProduct(id, controller.signal)
      .then((result) => {
        if (active) setProduct(result);
      })
      .catch((e) => {
        if (!active) return;
        setError(
          e.name === "AbortError"
            ? "Kết nối quá thời gian. Vui lòng thử lại."
            : e.message,
        );
      })
      .finally(() => window.clearTimeout(timeout));
    return () => {
      active = false;
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, [id]);
  return (
    <section className="detail-page">
      <a className="back-link" href={shopLink("/products")}>
        ← Quay lại danh mục
      </a>
      {error ? (
        <div className="catalog-state" role="alert">
          <h1>{error}</h1>
        </div>
      ) : !product ? (
        <p role="status">Đang tải sản phẩm…</p>
      ) : (
        <div className="detail-layout">
          <div className="detail-image">
            <ProductImage product={product} />
            {isDemo && <small>Bao bì minh họa cho giao diện mẫu</small>}
          </div>
          <div className="detail-info">
            <span className="eyebrow">{product.brand}</span>
            <h1>{product.name}</h1>
            <span className="stock-label">
              {product.availableStock > 0 ? "● Còn hàng" : "Hết hàng"}
            </span>
            <p className="detail-price">{formatPrice(product.price)}</p>
            <dl>
              <div>
                <dt>Danh mục</dt>
                <dd>{product.categoryName}</dd>
              </div>
              <div>
                <dt>Khối lượng</dt>
                <dd>{product.weightGrams.toLocaleString("vi")} g</dd>
              </div>
              <div>
                <dt>Độ tuổi</dt>
                <dd>
                  {["PUPPY", "KITTEN"].includes(product.lifeStage)
                    ? "Thú cưng con"
                    : product.lifeStage === "ADULT"
                      ? "Trưởng thành"
                      : product.lifeStage}
                </dd>
              </div>
            </dl>
            <p className="detail-description">{product.description}</p>
            <button
              className="button"
              disabled={cartBusy || product.availableStock < 1}
              onClick={() => onAdd(product)}
            >
              <Icon name="bag" size={18} />
              {isDemo ? "Thêm vào giỏ mẫu" : "Thêm vào giỏ hàng"}
            </button>
          </div>
        </div>
      )}
    </section>
  );
}
