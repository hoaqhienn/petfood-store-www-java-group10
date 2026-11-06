import { useState } from "react";
import { Icon } from "../shared/Icon";
import { shopLink } from "../shared/types";
import "./Hero.css";

const slides = [
  {
    eyebrow: "BỮA ĂN NHỎ · HẠNH PHÚC TO",
    title: (
      <>
        Món ngon cho bé,
        <br />
        <em>niềm vui cho bạn.</em>
      </>
    ),
    description:
      "Từ hạt giòn đến pâté mềm — tìm món hợp khẩu vị cho người bạn bốn chân.",
    cta: "Khám phá ngay",
    species: "",
  },
  {
    eyebrow: "DÀNH RIÊNG CHO BẠN NHỎ",
    title: (
      <>
        Một chiếc đuôi vẫy.
        <br />
        <em>Một bữa thật ngon.</em>
      </>
    ),
    description:
      "Khám phá thức ăn cho chó theo độ tuổi, khẩu vị và ngân sách của bạn.",
    cta: "Chọn món cho chó",
    species: "DOG",
  },
];
export function Hero() {
  const [active, setActive] = useState(0);
  const slide = slides[active];
  return (
    <>
      <section className="shop-hero" aria-label="Bộ sưu tập thức ăn">
        <div className="hero-content" key={active}>
          <span className="hero-eyebrow">{slide.eyebrow}</span>
          <h1>{slide.title}</h1>
          <p>{slide.description}</p>
          <a
            className="button button--dark"
            href={shopLink(
              "/products",
              slide.species ? { species: slide.species } : {},
            )}
          >
            {slide.cta}
            <Icon name="arrow" size={16} />
          </a>
        </div>
        <div className="hero-orbit" aria-hidden="true" />
        <img
          className="hero-dog"
          src="/images/golden-retriever.jpg"
          alt="Chú chó Golden Retriever"
        />
        <span className="hero-sticker">
          <Icon name="heart" size={20} />
          good food,
          <br />
          <strong>happy pets.</strong>
        </span>
        <div className="hero-controls">
          {slides.map((_, index) => (
            <button
              key={index}
              aria-label={`Xem banner ${index + 1}`}
              aria-pressed={active === index}
              className={active === index ? "active" : ""}
              onClick={() => setActive(index)}
            />
          ))}
        </div>
        <span className="hero-number">0{active + 1} / 02</span>
      </section>
      <div className="category-banners">
        <a
          className="category-banner category-banner--dog"
          href={shopLink("/products", { species: "DOG" })}
        >
          <div>
            <span>CHO NHỮNG CHIẾC ĐUÔI HAY VẪY</span>
            <h2>Món ngon cho cún</h2>
            <p>
              Hạt khô & thức ăn mềm <Icon name="arrow" size={17} />
            </p>
          </div>
          <span className="category-animal" aria-hidden="true">
            🐕
          </span>
        </a>
        <a
          className="category-banner category-banner--cat"
          href={shopLink("/products", { species: "CAT" })}
        >
          <div>
            <span>CHIỀU KHẨU VỊ CỦA “HOÀNG THƯỢNG”</span>
            <h2>Mèo mê, bạn cũng mê</h2>
            <p>
              Khám phá thực đơn cho mèo <Icon name="arrow" size={17} />
            </p>
          </div>
          <span className="category-animal" aria-hidden="true">
            🐈
          </span>
        </a>
      </div>
    </>
  );
}
