import { Icon } from "../shared/Icon";
import { shopLink } from "../shared/types";
import "./Footer.css";

export function Footer() {
  return (
    <footer className="store-footer">
      <div className="container footer-main">
        <div>
          <a className="logo" href={shopLink("/")}>
            <Icon name="paw" size={28} />
            petfood.
          </a>
          <p>
            Một bữa ngon, một chiếc đuôi vẫy.
            <br />
            Chọn món yêu thích cho bạn nhỏ mỗi ngày.
          </p>
        </div>
        <div>
          <h3>Khám phá cửa hàng</h3>
          <a href={shopLink("/products", { species: "DOG" })}>
            Thức ăn cho chó
          </a>
          <a href={shopLink("/products", { species: "CAT" })}>
            Thức ăn cho mèo
          </a>
          <a href={shopLink("/products")}>Tất cả sản phẩm</a>
        </div>
        <div>
          <h3>Về PetFood</h3>
          <p>
            Đồ án Lập trình WWW Java
            <br />
            DHKTPM19ATT · Nhóm 10
            <br />
            Giao diện React & TypeScript
          </p>
        </div>
        <div className="footer-motto">
          <Icon name="heart" size={25} />
          <p>
            Small meals.
            <br />
            <strong>Big happiness.</strong>
          </p>
        </div>
      </div>
      <div className="container footer-bottom">
        <span>© 2026 PetFood · Đề tài bán thức ăn thú cưng</span>
        <span>Made with care, for little paws.</span>
      </div>
    </footer>
  );
}
