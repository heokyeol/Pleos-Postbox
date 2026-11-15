"""웹 미리보기 서버. 같은 와이파이의 패드·휴대폰에서도 접속할 수 있다.

- web/ 폴더를 그대로 보여준다(캐시하지 않음 → 고친 파일이 바로 반영).
- 미리보기 모드에서 보낸 편지를 POST /api/letters 로 받아 tools/dummy/web_letters.json 에 쌓는다.
  더미 편지(letters.json)를 만들 때 이 파일을 쓴다. GET /api/letters 로 지금까지 받은 편지를 볼 수 있다.
- Supabase를 붙이면(web/config.js) 웹은 이 서버 대신 Supabase로 보낸다.

실행: python3 tools/dev_server.py [포트=8090]
"""
import json, os, socket, sys, threading, uuid
from datetime import datetime, timezone
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEB = os.path.join(ROOT, "web")
OUT = os.path.join(ROOT, "tools", "dummy", "web_letters.json")
MAX_BODY = 5 * 1024 * 1024
lock = threading.Lock()


def load():
    try:
        with open(OUT, encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        return []


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=WEB, **kw)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def _json(self, code, body):
        data = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path.split("?")[0] == "/api/letters":
            return self._json(200, load())
        super().do_GET()

    def do_POST(self):
        if self.path.split("?")[0] != "/api/letters":
            return self._json(404, {"error": "not found"})
        n = int(self.headers.get("Content-Length") or 0)
        if n <= 0 or n > MAX_BODY:
            return self._json(400, {"error": "bad size"})
        try:
            letter = json.loads(self.rfile.read(n))
            assert isinstance(letter, dict) and isinstance(letter.get("strokes"), list)
        except Exception:
            return self._json(400, {"error": "bad json"})
        letter = {**letter, "id": str(uuid.uuid4()),
                  "created_at": datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds"),
                  "opened_at": None}
        with lock:
            items = load()
            items.append(letter)
            os.makedirs(os.path.dirname(OUT), exist_ok=True)
            tmp = OUT + ".tmp"
            with open(tmp, "w", encoding="utf-8") as f:
                json.dump(items, f, ensure_ascii=False, separators=(",", ":"))
            os.replace(tmp, OUT)
        print(f"편지 저장: {letter.get('sender_name')} (모두 {len(items)}통) → {os.path.relpath(OUT, ROOT)}", flush=True)
        self._json(201, {"id": letter["id"], "count": len(items)})


def lan_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("192.168.0.1", 9))  # 실제로 보내지 않는다. 바깥으로 나가는 인터페이스 주소만 얻는다
        return s.getsockname()[0]
    except OSError:
        return None


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8090
    ip = lan_ip()
    print(f"웹 미리보기: http://localhost:{port}" + (f"  /  같은 와이파이: http://{ip}:{port}" if ip else ""), flush=True)
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
