"""Run against a disposable PetFood demo stack with Mailpit."""

import json
import os
import time
import uuid
from http.cookiejar import CookieJar
from urllib.error import URLError
from urllib.request import HTTPCookieProcessor, Request, build_opener, urlopen

base = os.getenv("PETFOOD_URL", "http://localhost:5173")
mailpit = os.getenv("MAILPIT_URL", "http://localhost:8025")
browser = build_opener(HTTPCookieProcessor(CookieJar()))
email = f"smoke-{uuid.uuid4().hex}@example.test"
password = uuid.uuid4().hex
headers = {}


def api(path, payload=None, expected=200, extra_headers=None):
    data = None if payload is None else json.dumps(payload).encode()
    request = Request(base + "/api/v1" + path, data, {
        "Content-Type": "application/json", **headers, **(extra_headers or {}),
    })
    with browser.open(request, timeout=10) as response:
        assert response.status == expected, (path, response.status, expected)
        return json.load(response)


def refresh_csrf():
    token = api("/auth/csrf")
    headers.clear()
    headers[token["headerName"]] = token["token"]


def read_mail(path):
    with urlopen(mailpit + "/api/v1/" + path, timeout=5) as response:
        return json.load(response)


def verify_mail(order_code):
    deadline = time.monotonic() + 60
    subjects = set()
    while time.monotonic() < deadline:
        try:
            for msg in read_mail("messages")["messages"] or []:
                if email not in {recipient["Address"] for recipient in msg["To"]}:
                    continue
                if msg["Subject"] == "PetFood | Xác nhận đơn hàng COD":
                    assert order_code in read_mail("message/" + msg["ID"])["Text"]
                subjects.add(msg["Subject"])
        except URLError:
            time.sleep(2)
            continue
        if {"PetFood | Đăng ký thành công", "PetFood | Xác nhận đơn hàng COD"} <= subjects:
            return
        time.sleep(2)
    raise AssertionError(f"Mailpit did not receive both expected emails. Found {subjects!r}")


def main():
    catalog = api("/products?size=100")
    product = next(item for item in catalog["items"] if item["availableStock"] > 0)
    api("/cart")
    refresh_csrf()
    cart = api("/cart/items", {"productId": product["id"], "quantity": 1, "expectedRevision": 0})
    account = api("/auth/register", {
        "email": email, "password": password, "displayName": "Smoke buyer",
    }, expected=201)
    assert account["emailStatus"] == "PENDING"
    api("/auth/login", {"email": email, "password": password})
    refresh_csrf()
    quote = api("/checkout/preview", {"cartRevision": cart["revision"]})
    order = api("/orders", {
        "cartToken": quote["cartToken"],
        "cartRevision": quote["cartRevision"],
        "quoteToken": quote["quoteToken"],
        "paymentMethod": "COD",
        "recipient": {"name": "Smoke buyer", "phone": "0900000000", "address": "Local smoke test"},
    }, expected=201, extra_headers={"Idempotency-Key": uuid.uuid4().hex})
    assert order["status"] == "PENDING" and order["paymentMethod"] == "COD"
    assert api("/orders/" + order["id"])["orderCode"] == order["orderCode"]
    assert api("/cart")["items"] == []
    verify_mail(order["orderCode"])
    print("COD order and both Mailpit emails verified.")


if __name__ == "__main__":
    main()
