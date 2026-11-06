import type { Product } from "../../shared/types";
import { isDemo, shopLink } from "../../shared/types";
import { useProducts } from "./useProducts";
import { ProductCard } from "../../components/ProductCard";
import { Icon } from "../../shared/Icon";
import "./Catalog.css";

export function ProductListing({
  home = false,
  onAdd,
}: {
  home?: boolean;
  onAdd: (product: Product) => void;
}) {
  const params = new URLSearchParams(window.location.search);
  const species = params.get("species") || "";
  const q = params.get("q") || "";
  const sort = params.get("sort") || "name,asc";
  const rawPage = Number(params.get("page") || 0);
  const page = Number.isSafeInteger(rawPage) && rawPage >= 0 ? rawPage : 0;
  const query = new URLSearchParams({
    q,
    species,
    sort,
    page: String(page),
    size: "12",
  }).toString();
  const { data, error } = useProducts(query);
  const title = home
    ? "Món ngon được chọn mỗi ngày"
    : q
      ? `Kết quả cho “${q}”`
      : species === "DOG"
        ? "Thức ăn cho chó"
        : species === "CAT"
          ? "Thức ăn cho mèo"
          : "Tất cả sản phẩm";
  return (
    <section className="product-section" id="products">
      <div className="section-title">
        <div>
          <span className="eyebrow">
            {home ? "THỰC ĐƠN CHO BẠN NHỎ" : "TÌM MÓN HỢP KHẨU VỊ"}
          </span>
          {home ? <h2>{title}</h2> : <h1>{title}</h1>}
        </div>
        {home && (
          <a href={shopLink("/products")}>
            Xem tất cả <Icon name="arrow" size={15} />
          </a>
        )}
      </div>
      <div className="catalog-toolbar">
        <div className="catalog-tabs">
          {[
            ["", "Tất cả"],
            ["DOG", "Cho chó"],
            ["CAT", "Cho mèo"],
          ].map(([value, label]) => (
            <a
              key={value}
              className={species === value ? "active" : ""}
              aria-current={species === value ? "page" : undefined}
              href={shopLink("/products", { species: value, q, sort })}
            >
              {label}
            </a>
          ))}
        </div>
        <label className="sort-label">
          <span>Sắp xếp:</span>
          <select
            aria-label="Sắp xếp sản phẩm"
            value={sort}
            onChange={(event) =>
              window.location.assign(
                shopLink("/products", { species, q, sort: event.target.value }),
              )
            }
          >
            <option value="name,asc">Tên A → Z</option>
            <option value="price,asc">Giá thấp → cao</option>
            <option value="price,desc">Giá cao → thấp</option>
          </select>
        </label>
      </div>
      {error ? (
        <div className="catalog-state" role="alert">
          <Icon name="bag" size={36} />
          <h3>Danh mục chưa sẵn sàng</h3>
          <p>{error}</p>
          <a className="button" href="/products?source=demo">
            Xem giao diện với dữ liệu mẫu
          </a>
          <button
            className="text-button"
            onClick={() => window.location.reload()}
          >
            Thử kết nối lại
          </button>
        </div>
      ) : !data ? (
        <div
          className="product-grid"
          aria-label="Đang tải sản phẩm"
          role="status"
        >
          {Array.from({ length: 4 }, (_, i) => (
            <div className="product-skeleton" key={i} />
          ))}
        </div>
      ) : data.items.length === 0 ? (
        <div className="catalog-state">
          <Icon name="search" size={36} />
          <h3>Chưa tìm thấy món phù hợp</h3>
          <p>Thử tên ngắn hơn hoặc chọn lại loại thú cưng nhé.</p>
          <a className="button" href={shopLink("/products")}>
            Xem tất cả sản phẩm
          </a>
        </div>
      ) : (
        <>
          <div className="product-grid">
            {data.items.map((product) => (
              <ProductCard key={product.id} product={product} onAdd={onAdd} />
            ))}
          </div>
          {!home && (
            <p className="result-count">
              Đang hiển thị {data.items.length} / {data.totalElements} sản phẩm{" "}
              {isDemo ? "mẫu" : ""}
            </p>
          )}
          <nav className="pagination" aria-label="Phân trang">
            {data.page > 0 && (
              <a
                className="button"
                href={shopLink("/products", {
                  species,
                  q,
                  sort,
                  page: String(data.page - 1),
                })}
              >
                Trang trước
              </a>
            )}
            {data.page + 1 < data.totalPages && (
              <a
                className="button"
                href={shopLink("/products", {
                  species,
                  q,
                  sort,
                  page: String(data.page + 1),
                })}
              >
                Trang sau
              </a>
            )}
          </nav>
        </>
      )}
    </section>
  );
}
