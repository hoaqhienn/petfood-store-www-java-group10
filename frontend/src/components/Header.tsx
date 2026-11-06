import { Icon } from "../shared/Icon";
import { isDemo, shopLink } from "../shared/types";
import "./Header.css";

export function Header({
  count,
  onCart,
}: {
  count: number;
  onCart: () => void;
}) {
  const params = new URLSearchParams(window.location.search);
  return (
    <>
      <div className="topbar">
        <div className="container">
          <span>Mỗi bữa ngon, thêm một niềm vui.</span>
          <span>
            Thức ăn cho chó & mèo <i /> <a href="/admin">Quản trị</a>
          </span>
        </div>
      </div>
      <header className="store-header">
        <div className="container header-main">
          <a
            href={shopLink("/")}
            className="logo"
            aria-label="PetFood trang chủ"
          >
            <span className="logo-mark">
              <Icon name="paw" size={26} />
            </span>
            <span>
              petfood<span className="logo-dot">.</span>
              <small>ĂN NGON · LỚN KHỎE</small>
            </span>
          </a>
          <form className="header-search" action="/products">
            <input
              type="hidden"
              name="source"
              value={isDemo ? "demo" : "api"}
            />
            <Icon name="search" size={19} />
            <input
              aria-label="Tìm thức ăn cho thú cưng"
              name="q"
              type="search"
              placeholder="Bạn đang tìm món gì cho bé?"
              defaultValue={params.get("q") || ""}
              maxLength={200}
            />
            <button type="submit">Tìm kiếm</button>
          </form>
          <a
            className="header-cart"
            href={shopLink("/cart")}
            onClick={(event) => {
              if (isDemo) {
                event.preventDefault();
                onCart();
              }
            }}
          >
            <span className="cart-icon">
              <Icon name="bag" size={25} />
              <b>{count}</b>
            </span>
            <span>
              Giỏ hàng
              <small>{isDemo ? "Giỏ mẫu của bạn" : "Giỏ hàng của bạn"}</small>
            </span>
          </a>
        </div>
      </header>
      <nav className="main-nav" aria-label="Điều hướng chính">
        <div className="container nav-inner">
          <a className="nav-category" href={shopLink("/products")}>
            <Icon name="menu" size={18} /> Danh mục sản phẩm
          </a>
          <a
            href={shopLink("/")}
            aria-current={window.location.pathname === "/" ? "page" : undefined}
          >
            Trang chủ
          </a>
          <a href={shopLink("/products", { species: "DOG" })}>
            Thức ăn cho chó
          </a>
          <a href={shopLink("/products", { species: "CAT" })}>
            Thức ăn cho mèo
          </a>
          <a href={shopLink("/products")}>Tất cả sản phẩm</a>
          <span className="nav-note">
            <Icon name="heart" size={15} /> Chọn món bằng cả yêu thương
          </span>
        </div>
      </nav>
    </>
  );
}
