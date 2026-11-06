import { useEffect, useState } from "react";
import { demoProducts } from "../../data/demoProducts";
import { isDemo, type ProductPage } from "../../shared/types";
import { getProducts } from "./catalogApi";

export function useProducts(query: string) {
  const [data, setData] = useState<ProductPage | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    setError("");
    setData(null);
    const controller = new AbortController();
    const params = new URLSearchParams(query);
    if (isDemo) {
      const q = (params.get("q") || "").toLocaleLowerCase("vi");
      let items = demoProducts.filter(
        (p) =>
          (!params.get("species") || p.species === params.get("species")) &&
          p.name.toLocaleLowerCase("vi").includes(q),
      );
      const sort = params.get("sort");
      if (sort?.startsWith("price"))
        items = [...items].sort(
          (a, b) =>
            (Number(a.price) - Number(b.price)) *
            (sort.endsWith("desc") ? -1 : 1),
        );
      if (sort === "name,asc")
        items = [...items].sort((a, b) => a.name.localeCompare(b.name, "vi"));
      setData({ items, page: 0, totalPages: 1, totalElements: items.length });
      return;
    }
    let active = true;
    const timeout = window.setTimeout(() => controller.abort(), 8000);
    getProducts(query, controller.signal)
      .then((result) => {
        if (active) setData(result);
      })
      .catch((e) => {
        if (!active) return;
        setError(
          e instanceof Error && e.name !== "AbortError"
            ? e.message
            : "Kết nối quá thời gian. Hãy kiểm tra máy chủ và thử lại.",
        );
      })
      .finally(() => window.clearTimeout(timeout));
    return () => {
      active = false;
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, [query]);
  return { data, error };
}
