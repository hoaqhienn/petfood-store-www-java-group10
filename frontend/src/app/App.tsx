import { useEffect, useState } from 'react';
import { getProjectInfo } from '../shared/api';
import { ProductDetail, ProductList } from '../features/catalog/Catalog';

const repo = 'https://github.com/hoaqhienn/petfood-store-www-java-group10';

export function App() {
  const [connection, setConnection] = useState('Đang kiểm tra kết nối API…');
  const isAdmin = window.location.pathname === '/admin' || window.location.pathname.startsWith('/admin/');
  const catalogPath = window.location.pathname.replace(/\/$/, '');
  const productRoute = /^\/products\/([^/]+)$/.exec(catalogPath);
  const isCatalog = catalogPath === '/products' || productRoute !== null;

  useEffect(() => {
    if (isCatalog) return;
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 8000);
    getProjectInfo(controller.signal)
      .then((info) => setConnection(`API đã kết nối · v${info.version}`))
      .catch(() => setConnection('API chưa sẵn sàng · xem hướng dẫn khởi động trong README'));
    return () => { window.clearTimeout(timeout); controller.abort(); };
  }, [isCatalog]);

  return (
    <>
      <a className="skip-link" href="#main">Đến nội dung chính</a>
      <header className="site-header">
        <a className="wordmark" href="/" aria-label="PetFood trang chủ">petfood<span>·</span></a>
        <nav aria-label="Điều hướng chính">
          <a href="/" aria-current={!isAdmin && !isCatalog ? 'page' : undefined}>Giới thiệu</a>
          <a href="/products" aria-current={isCatalog ? 'page' : undefined}>Sản phẩm</a>
          <a href="/admin" aria-current={isAdmin ? 'page' : undefined}>Quản trị</a>
          <a href={`${repo}#readme`} target="_blank" rel="noreferrer">Tài liệu ↗</a>
        </nav>
      </header>
      <main id="main">
        {isCatalog ? (productRoute ? <ProductDetail id={productRoute[1]} /> : <ProductList />) : <>
        <section className="hero">
          <div>
            <p className="eyebrow">PETFOOD / PROJECT STARTER</p>
            <h1>{isAdmin ? <>A simple start.<br /><em>Built together.</em></> : <>Good food.<br /><em>Happy pets.</em></>}</h1>
            <p className="intro">{isAdmin
              ? 'Không gian quản trị đang được xây dựng. Sản phẩm, tài khoản và đơn hàng sẽ được quản lý tại đây.'
              : 'Mua thức ăn cho chó mèo, đơn giản hơn mỗi ngày. Chúng tôi đang xây dựng trải nghiệm chọn đúng món và đặt hàng thuận tiện.'}</p>
            <div className="actions">
              {!isAdmin && <a className="button" href="/products">Xem sản phẩm</a>}
              <a className="button" href={`${repo}/blob/main/docs/LOCAL_DEVELOPMENT.md`} target="_blank" rel="noreferrer">Hướng dẫn chạy dự án <span>↗</span></a>
              <a className="text-link" href="#roadmap">Xem lộ trình ↓</a>
            </div>
          </div>
          <aside className="project-card" aria-label="Tiến độ dự án">
            <span className="card-label">THE FIRST CHAPTER</span>
            <div className="bowl" aria-hidden="true"><i /><i /><i /><i /><i /></div>
            <h2>Nền tảng hôm nay.<br />Trải nghiệm ngày mai.</h2>
            <p>Bộ khung phát triển · Tháng 11/2026</p>
            <div className="api-status" role="status">{connection}</div>
          </aside>
        </section>
        <section className="roadmap" id="roadmap" aria-labelledby="roadmap-title">
          <div className="section-heading"><p className="eyebrow">WHAT COMES NEXT</p><h2 id="roadmap-title">Từng bước, rõ ràng.</h2></div>
          <div className="cards">
            <article><span>01 / NỀN TẢNG</span><h3>Sẵn sàng phát triển</h3><p>React, Spring Boot, PostgreSQL, Redis và môi trường Docker dùng chung.</p><strong>Bộ khung hiện tại</strong></article>
            <article><span>02 / MUA SẮM</span><h3>Từ chọn món đến đặt hàng</h3><p>Danh mục, tài khoản, giỏ hàng Session, COD, email và quản trị.</p><strong>Dự kiến triển khai</strong></article>
            <article><span>03 / CÁ NHÂN HÓA</span><h3>Hiểu bạn nhỏ hơn</h3><p>Hồ sơ thú cưng, tìm kiếm vector, tư vấn AI và phân tích hành vi.</p><strong>Dự kiến triển khai</strong></article>
          </div>
        </section>
        {isAdmin && <p className="notice">Đây là trang giữ chỗ công khai, chưa có dữ liệu hoặc thao tác quản trị. Phân quyền admin sẽ được triển khai cùng đăng nhập.</p>}
        </>}
      </main>
      <footer><span>petfood — Good food. Happy pets.</span><span>DHKTPM19AT · Nhóm 10 · Đề tài 42</span></footer>
    </>
  );
}

