import { useEffect, useState } from "react";
import { Header } from "../components/Header";
import { Footer } from "../components/Footer";
import { Sidebar } from "../components/Sidebar";
import { Hero } from "../components/Hero";
import { ProductListing } from "../features/catalog/ProductListing";
import { ProductDetail } from "../features/catalog/ProductDetail";
import { CartDrawer } from "../features/cart/CartDrawer";
import { useCart } from "../features/cart/useCart";
import {
  useSessionCart,
  CartBusyContext,
} from "../features/cart/useSessionCart";
import { CartPage } from "../features/cart/CartPage";
import { Icon } from "../shared/Icon";
import { isDemo, shopLink } from "../shared/types";

export function App() {
  const [cartOpen, setCartOpen] = useState(false);
  const demoCart = useCart();
  const sessionCart = useSessionCart(!isDemo);
  const cart = isDemo ? demoCart : sessionCart;
  const path = window.location.pathname.replace(/\/$/, "") || "/";
  const detail = /^\/products\/([^/]+)$/.exec(path);
  const isAdmin = path === "/admin" || path.startsWith("/admin/");
  const home = path === "/";
  useEffect(() => {
    if (!cart.message) return;
    const timer = window.setTimeout(cart.clearMessage, 3500);
    return () => window.clearTimeout(timer);
  }, [cart.message]);
  return (
    <CartBusyContext.Provider
      value={!isDemo && (sessionCart.busy || !sessionCart.ready)}
    >
      <a className="skip-link" href="#main">
        Đến nội dung chính
      </a>
      <Header count={cart.count} onCart={() => setCartOpen(true)} />
      <main className="container" id="main">
        <div className="breadcrumb">
          <span>Trang chủ</span>
          <span>/</span>
          <span>
            {home
              ? "Cửa hàng thú cưng"
              : isAdmin
                ? "Quản trị"
                : detail
                  ? "Chi tiết sản phẩm"
                  : path === "/cart"
                    ? "Giỏ hàng"
                    : "Sản phẩm"}
          </span>
        </div>
        {!isDemo && sessionCart.error && path !== "/cart" && (
          <div className="notice" role="alert">
            {sessionCart.error}{" "}
            <a href={shopLink("/cart")}>Kiểm tra giỏ hàng</a>
          </div>
        )}
        <div className="preview-notice">
          <span>
            <i />
            {isDemo
              ? "Bản xem thử · Sản phẩm, giá và giỏ hàng là dữ liệu mẫu."
              : "Danh mục đang lấy dữ liệu từ API Spring Boot."}
          </span>
          <a href={isDemo ? "/products?source=api" : "/?source=demo"}>
            {isDemo ? "Xem dữ liệu API" : "Về bản xem thử"}{" "}
            <Icon name="arrow" size={12} />
          </a>
        </div>
        {isAdmin ? (
          <section className="simple-page">
            <span className="eyebrow">KHÔNG GIAN QUẢN TRỊ</span>
            <h1>Quản lý cửa hàng</h1>
            <p>
              Trang quản trị chưa triển khai. Đăng nhập, phân quyền và quản lý
              sản phẩm sẽ được thực hiện ở phần Spring Boot.
            </p>
            <a className="button" href={shopLink("/")}>
              Quay về cửa hàng
            </a>
          </section>
        ) : path === "/cart" && !isDemo ? (
          <CartPage state={sessionCart} />
        ) : detail ? (
          <ProductDetail id={decodeURIComponent(detail[1])} onAdd={cart.add} />
        ) : home || path === "/products" ? (
          <div className="store-layout">
            <Sidebar />
            <div className="store-content">
              {home && <Hero />}
              <ProductListing home={home} onAdd={cart.add} />
            </div>
          </div>
        ) : (
          <section className="simple-page">
            <h1>Trang không tồn tại</h1>
            <a className="button" href={shopLink("/")}>
              Về trang chủ
            </a>
          </section>
        )}
      </main>
      <Footer />
      {cart.message && (
        <div className="toast" role="status">
          <Icon name="check" size={20} />
          <span>{cart.message}</span>
          <button
            className="icon-button"
            onClick={cart.clearMessage}
            aria-label="Đóng thông báo"
          >
            <Icon name="close" size={16} />
          </button>
        </div>
      )}
      {cartOpen && isDemo && (
        <CartDrawer
          items={demoCart.items}
          onClose={() => setCartOpen(false)}
          onChange={demoCart.change}
        />
      )}
    </CartBusyContext.Provider>
  );
}
