"""차량 앱 더미 편지(app/src/main/assets/letters.json)를 만든다.

진짜 더미 그림을 받으면 이 스크립트 대신 그 JSON을 letters.json으로 바꿔 넣으면 된다.
형식은 웹이 보내는 편지와 같다(Letter 배열). opened_at이 null인 편지가 '새 편지'다.
실행: python3 tools/make_dummy_letters.py
"""
import json, math, os

W, H = 1600, 900  # 16:9 기준 좌표로 그리고 0..1로 정규화
ASPECT = 16 / 9
INK = dict(k="#000000", red="#E5484D", orange="#F76B15", yellow="#FFC53D",
           green="#30A46C", blue="#0090FF", purple="#8E4EC6", pink="#E93D82")
ERASE = "#00000000"


class Pen:
    def __init__(self):
        self.t = 0
        self.strokes = []
        self.texts = []

    def stroke(self, pts, color, width=0.008, step=14):
        out = []
        for x, y in pts:
            out.append({"x": round(x / W, 4), "y": round(y / H, 4), "t": self.t})
            self.t += step
        self.strokes.append({"color": color, "width": width, "points": out})
        self.t += 260  # 펜을 떼고 다음 획까지

    def text(self, x, y, s, color="#000000", size=0.045):
        self.t += 400
        self.texts.append({"x": round(x / W, 4), "y": round(y / H, 4), "text": s,
                           "color": color, "size": size, "t": self.t})
        self.t += 600


def line(a, b, n=12):
    return [(a[0] + (b[0] - a[0]) * i / n, a[1] + (b[1] - a[1]) * i / n) for i in range(n + 1)]


def poly(*pts, n=10):
    out = []
    for a, b in zip(pts, pts[1:]):
        out += line(a, b, n)[(1 if out else 0):]
    return out


def circle(cx, cy, r, n=36, start=-math.pi / 2):
    return [(cx + r * math.cos(start + 2 * math.pi * i / n), cy + r * math.sin(start + 2 * math.pi * i / n))
            for i in range(n + 1)]


def heart(cx, cy, s, n=48):
    pts = []
    for i in range(n + 1):
        a = math.pi * 2 * i / n
        x = 16 * math.sin(a) ** 3
        y = 13 * math.cos(a) - 5 * math.cos(2 * a) - 2 * math.cos(3 * a) - math.cos(4 * a)
        pts.append((cx + x * s, cy - y * s))
    return pts


def wave(x0, x1, y, amp=10, n=40):
    return [(x0 + (x1 - x0) * i / n, y + amp * math.sin(i / n * math.pi * 3)) for i in range(n + 1)]


def car(p, dx=0, dy=0):
    m = lambda pts: [(x + dx, y + dy) for x, y in pts]
    p.stroke(m(wave(150, 1460, 712, 8)), INK["green"])
    p.stroke(m(poly((420, 640), (410, 560), (510, 514), (622, 506), (700, 420), (850, 368),
                    (1000, 366), (1110, 380), (1180, 506), (1300, 528), (1374, 560), (1360, 640), (420, 640))),
             INK["red"])
    p.stroke(m(poly((668, 498), (720, 440), (818, 408), (826, 500), (668, 498))), INK["blue"])
    p.stroke(m(poly((876, 406), (998, 404), (1080, 440), (1130, 498), (880, 500), (876, 406))), INK["blue"])
    p.stroke(m(circle(602, 652, 60)), INK["k"])
    p.stroke(m(circle(1182, 650, 62)), INK["k"])


def sun(p, cx=1332, cy=186, r=82):
    p.stroke(circle(cx, cy, r), INK["yellow"])
    for i in range(8):
        a = i * math.pi / 4
        p.stroke(line((cx + (r + 26) * math.cos(a), cy + (r + 26) * math.sin(a)),
                      (cx + (r + 70) * math.cos(a), cy + (r + 70) * math.sin(a)), 4), INK["yellow"])


def flower(p, cx, cy, color):
    p.stroke(line((cx, cy + 40), (cx, cy + 260), 16), INK["green"])
    for i in range(5):
        a = i * 2 * math.pi / 5 - math.pi / 2
        p.stroke(circle(cx + 52 * math.cos(a), cy + 52 * math.sin(a), 36, n=20), color)
    p.stroke(circle(cx, cy, 22, n=16), INK["yellow"])


def from_image(id_, sender, created, opened, image):
    """dummy-input/의 선 그림 이미지를 획으로 바꿔 편지로 (tools/image_to_letter.py)."""
    import sys
    sys.path.insert(0, os.path.dirname(__file__))
    from image_to_letter import convert
    body = convert(os.path.join(os.path.dirname(__file__), "..", "dummy-input", image))
    return {"id": id_, "family_code": "K7Q2MX", "sender_name": sender, "created_at": created,
            "opened_at": opened, **body}


def letter(id_, sender, created, opened, draw):
    p = Pen()
    draw(p)
    return {"id": id_, "family_code": "K7Q2MX", "sender_name": sender, "created_at": created,
            "aspect": round(ASPECT, 4), "strokes": p.strokes, "text": None, "opened_at": opened,
            "texts": p.texts}


def l_new_car(p):
    car(p)
    sun(p)
    p.stroke(heart(300, 250, 7.5), INK["pink"])
    p.text(480, 770, "아빠 오늘도 안전운전!", size=0.046)


def l_new_sky(p):
    p.stroke(heart(800, 430, 16), INK["red"], width=0.016)
    p.stroke(line((560, 760), (1040, 760), 20), INK["k"])
    p.stroke(line((560, 760), (1040, 760), 20), ERASE, width=0.03)  # 지우개 획 예시: 방금 그은 줄을 지운다
    sun(p, 1360, 170, 70)


def l_old_car(p):
    car(p)


def l_old_mom(p):
    p.stroke(heart(800, 360, 13), INK["pink"], width=0.012)
    p.text(520, 660, "퇴근 조심히 와요", size=0.05)


def l_old_flowers(p):
    flower(p, 500, 340, INK["purple"])
    flower(p, 800, 300, INK["orange"])
    flower(p, 1100, 360, INK["pink"])
    p.stroke(wave(200, 1400, 640, 6), INK["green"])


def l_old_house(p):
    p.stroke(poly((560, 420), (800, 220), (1040, 420)), INK["red"])
    p.stroke(poly((600, 420), (600, 720), (1000, 720), (1000, 420)), INK["k"])
    p.stroke(poly((750, 720), (750, 580), (850, 580), (850, 720)), INK["blue"])
    p.text(620, 780, "집에서 기다릴게", size=0.04)


letters = [
    letter("dummy-1", "지우민준엄마❤️", "2026-11-10T14:30:00+09:00", "2026-11-10T18:40:00+09:00", l_old_mom),
    letter("dummy-2", "민준", "2026-11-10T18:05:00+09:00", "2026-11-11T08:10:00+09:00", l_old_car),
    letter("dummy-3", "지우", "2026-11-10T08:01:00+09:00", "2026-11-10T18:40:00+09:00", l_old_flowers),
    letter("dummy-4", "지우민준엄마❤️", "2026-11-11T16:20:00+09:00", "2026-11-11T18:30:00+09:00", l_old_house),
    letter("dummy-5", "지우", "2026-11-12T19:40:00+09:00", None, l_new_sky),
    letter("dummy-6", "민준", "2026-11-12T20:12:00+09:00", None, l_new_car),
    from_image("dummy-7", "지우", "2026-11-12T21:05:00+09:00", None, "더미1.png"),
]

out = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "letters.json")
with open(out, "w", encoding="utf-8") as f:
    json.dump(letters, f, ensure_ascii=False, separators=(",", ":"))
print(out, sum(len(s["points"]) for l in letters for s in l["strokes"]), "points")
