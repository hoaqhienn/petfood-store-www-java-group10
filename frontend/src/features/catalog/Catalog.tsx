import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';

interface Product {
  id: string;
  name: string;
  description: string;
  brand: string;
  categoryName: string;
  weightGrams: number;
  price: string;
  availableStock: number;
}

interface ProductPage {
  items: Product[];
  page: number;
  totalPages: number;
}

type Result<T> = { state: 'loading' | 'missing' | 'error' } | { state: 'ready'; data: T };

function useCatalog<T>(path: string): Result<T> {
  const [result, setResult] = useState<Result<T>>({ state: 'loading' });
  useEffect(() => {
    const request = new AbortController();
    const timeout = window.setTimeout(() => {
      request.abort();
      setResult({ state: 'error' });
    }, 8000);
    setResult({ state: 'loading' });
    async function load() {
      try {
        const response = await fetch('/api/v1' + path, { signal: request.signal });
        if (response.status === 404) {
          if (!request.signal.aborted) setResult({ state: 'missing' });
          return;
        }
        if (!response.ok) throw new Error('Catalog request failed');
        const data: T = await response.json();
        if (!request.signal.aborted) setResult({ state: 'ready', data });
      } catch {
        if (!request.signal.aborted) setResult({ state: 'error' });
      } finally {
        window.clearTimeout(timeout);
      }
    }
    void load();
    return () => {
      window.clearTimeout(timeout);
      request.abort();
    };
  }, [path]);
  return result;
}

const vnd = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' });

function ProductFacts({ product }: { product: Product }) {
  return (
    <>
      <p>{product.brand} · {product.weightGrams} g</p>
      <strong>{vnd.format(BigInt(product.price))}</strong>
      <p>{product.availableStock > 0 ? 'Còn hàng' : 'Hết hàng'}</p>
    </>
  );
}

function CatalogStatus({ state }: { state: 'loading' | 'missing' | 'error' }) {
  if (state === 'loading') return <p role="status">Đang tải sản phẩm…</p>;
  if (state === 'missing') return <p>Không tìm thấy sản phẩm.</p>;
  return <p role="alert">Danh mục tạm thời chưa khả dụng. <button type="button" onClick={() => window.location.reload()}>Thử lại</button></p>;
}

export function ProductList() {
  const params = new URLSearchParams(window.location.search);
  const q = params.get('q') ?? '';
  const pageParam = params.get('page') ?? '0';
  const result = useCatalog<ProductPage>('/products?' + new URLSearchParams({ q, page: pageParam }));
  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = String(new FormData(event.currentTarget).get('q') ?? '').trim();
    window.location.assign('/products' + (query ? '?' + new URLSearchParams({ q: query }) : ''));
  }
  function pageLink(page: number) {
    return '/products?' + new URLSearchParams({ q, page: String(page) });
  }
  return (
    <section className="roadmap catalog">
      <h1>Thức ăn cho chó mèo</h1>
      <form className="catalog-search" onSubmit={search}>
        <label htmlFor="catalog-query">Tìm theo tên hoặc mã sản phẩm</label>
        <div>
          <input id="catalog-query" name="q" type="search" defaultValue={q} maxLength={200} />
          <button type="submit">Tìm</button>
        </div>
      </form>
      {result.state !== 'ready' ? <CatalogStatus state={result.state} /> : (
        <>
          {result.data.items.length === 0 ? <p>Không có sản phẩm phù hợp.</p> : (
            <div className="cards">
              {result.data.items.map(product => (
                <article key={product.id}>
                  <span>{product.categoryName}</span>
                  <h3><a href={'/products/' + encodeURIComponent(product.id)}>{product.name}</a></h3>
                  <ProductFacts product={product} />
                </article>
              ))}
            </div>
          )}
          <nav aria-label="Phân trang sản phẩm" className="catalog-pagination">
            {result.data.page > 0 && <a href={pageLink(result.data.page - 1)}>Trang trước</a>}
            {result.data.page + 1 < result.data.totalPages && <a href={pageLink(result.data.page + 1)}>Trang sau</a>}
          </nav>
        </>
      )}
    </section>
  );
}

export function ProductDetail({ id }: { id: string }) {
  const result = useCatalog<Product>('/products/' + encodeURIComponent(id));
  return (
    <section className="roadmap catalog">
      <a href="/products">← Danh sách sản phẩm</a>
      {result.state !== 'ready' ? <CatalogStatus state={result.state} /> : (
        <>
          <p>{result.data.categoryName}</p>
          <h1>{result.data.name}</h1>
          <ProductFacts product={result.data} />
          <p>{result.data.description}</p>
        </>
      )}
    </section>
  );
}
