import { Icon } from "../shared/Icon";
import { shopLink } from "../shared/types";
import "./Sidebar.css";

export function Sidebar() {
  const species = new URLSearchParams(window.location.search).get("species");
  return (
    <aside className="sidebar">
      <div className="sidebar-categories">
        <h2>Khám phá theo thú cưng</h2>
        {[
          ["", "Tất cả sản phẩm", "bag"],
          ["DOG", "Thức ăn cho chó", "paw"],
          ["CAT", "Thức ăn cho mèo", "paw"],
        ].map(([value, label, icon]) => (
          <a
            key={value}
            href={shopLink("/products", value ? { species: value } : {})}
            className={species === value ? "selected" : ""}
          >
            <Icon name={icon as "bag" | "paw"} size={18} />
            <span>{label}</span>
            <Icon name="chevron" size={14} />
          </a>
        ))}
        <div className="sidebar-tip">
          <span>MỘT CHÚT QUAN TÂM</span>
          <p>
            Đúng độ tuổi.
            <br />
            Đúng khẩu vị.
            <br />
            <strong>Trọn niềm vui.</strong>
          </p>
          <Icon name="heart" size={29} />
          <small>Chọn thức ăn phù hợp với bạn nhỏ của bạn.</small>
        </div>
      </div>
      <div className="sidebar-help">
        <Icon name="paw" size={24} />
        <h3>Bữa ngon mỗi ngày</h3>
        <p>
          Tìm theo tên món, loại thú cưng hoặc sắp xếp theo mức giá bạn muốn.
        </p>
        <a href={shopLink("/products")}>
          Khám phá sản phẩm <Icon name="arrow" size={15} />
        </a>
      </div>
    </aside>
  );
}
