"""선 그림 이미지(PNG/JPG)를 편지 획(Letter JSON)으로 바꾼다.

이미지에는 획 순서가 없으므로 순서는 추정한다: 덩어리(글자·그림) 단위로 줄마다 위→아래, 왼쪽→오른쪽.
덩어리 안에서는 왼쪽 위 끝점부터 선을 따라가고, 갈림길에서는 가장 곧게 이어지는 쪽으로 간다.
흰 바탕에 펜으로 그린 선 그림에 맞다. 넓게 칠한 면·사진·음영은 잘 안 된다.

실행:
  python3 tools/image_to_letter.py 입력.png 출력.json [--preview 미리보기.png]
출력 JSON은 Letter 하나에서 id·sender_name·created_at·opened_at만 빠진 형태다
(make_dummy_letters.py가 채운다).
"""
import argparse, json, math
import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

ASPECT = 16 / 9
SCALE = 3          # 작은 이미지를 키워서 선 가운데를 더 매끈하게 찾는다
WHITE_CUT = 200    # 가장 어두운 채널이 이보다 밝으면 종이로 본다
SPEED = 0.6        # 원본 픽셀/ms. 재생할 때 앱이 속도를 다시 맞춘다
PEN_UP = 220       # 획 사이 쉬는 시간(ms)
# 웹 스케치북 색 8개. 색 무리를 나누는 기준으로만 쓰고, 획 색은 원본 색을 그대로 쓴다.
INKS = ["#000000", "#E5484D", "#F76B15", "#FFC53D", "#30A46C", "#0090FF", "#8E4EC6", "#E93D82"]


def hex_rgb(h):
    return np.array([int(h[i:i + 2], 16) for i in (1, 3, 5)], float)


def hue(px):
    r, g, b = (px[..., i] / 255.0 for i in range(3))
    mx, mn = np.maximum(np.maximum(r, g), b), np.minimum(np.minimum(r, g), b)
    d = np.where(mx - mn == 0, 1e-6, mx - mn)
    h = np.where(mx == r, ((g - b) / d) % 6, np.where(mx == g, (b - r) / d + 2, (r - g) / d + 4))
    return h * 60


def classify(rgb, mask):
    """잉크 픽셀을 색 무리로 나눈다. 색은 선 가운데(테두리 번짐 제외)에서 정하고,
    테두리 픽셀은 가장 가까운 가운데 픽셀의 색을 따른다. 무리마다 원본 색(중앙값)을 쓴다."""
    dist = ndimage.distance_transform_edt(mask)
    core = mask & (dist >= 2)
    px = rgb[core].astype(float)
    sat = px.max(1) - px.min(1)
    pal = [h for h in INKS if h != "#000000"]
    pal_h = hue(np.stack([hex_rgb(h) for h in pal]))
    hd = np.abs(hue(px)[:, None] - pal_h[None])
    hd = np.minimum(hd, 360 - hd)
    idx = hd.argmin(1) + 1          # 1.. = 팔레트 색상 계열
    idx[sat < 50] = 0               # 0 = 검정·회색
    cls = np.full(mask.shape, -1)
    cls[core] = idx
    # 테두리 → 가장 가까운 가운데 픽셀의 무리
    _, (iy, ix) = ndimage.distance_transform_edt(~core, return_indices=True)
    full = cls[iy, ix]
    full[~mask] = -1
    colors = {}
    for k in set(idx.tolist()):
        med = np.median(px[idx == k], axis=0) if k else np.array([0, 0, 0])
        colors[k] = "#%02X%02X%02X" % tuple(int(v) for v in med)
    return full, colors


def thin(mask):
    """Zhang-Suen 세선화: 선을 1픽셀 굵기 가운데선으로."""
    img = np.pad(mask.astype(np.uint8), 1)
    while True:
        changed = False
        for step in (0, 1):
            P = img
            p2, p3, p4 = P[:-2, 1:-1], P[:-2, 2:], P[1:-1, 2:]
            p5, p6, p7 = P[2:, 2:], P[2:, 1:-1], P[2:, :-2]
            p8, p9 = P[1:-1, :-2], P[:-2, :-2]
            nb = [p2, p3, p4, p5, p6, p7, p8, p9]
            B = sum(n.astype(int) for n in nb)
            A = sum(((nb[i] == 0) & (nb[(i + 1) % 8] == 1)).astype(int) for i in range(8))
            if step == 0:
                c = (p2 * p4 * p6 == 0) & (p4 * p6 * p8 == 0)
            else:
                c = (p2 * p4 * p8 == 0) & (p2 * p6 * p8 == 0)
            rm = (P[1:-1, 1:-1] == 1) & (B >= 2) & (B <= 6) & (A == 1) & c
            if rm.any():
                img[1:-1, 1:-1][rm] = 0
                changed = True
        if not changed:
            return img[1:-1, 1:-1].astype(bool)


NB = [(-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1)]


def neighbors(sk, y, x):
    h, w = sk.shape
    return [(y + dy, x + dx) for dy, dx in NB if 0 <= y + dy < h and 0 <= x + dx < w and sk[y + dy, x + dx]]


def trace(sk):
    """가운데선을 획(점 목록)들로. 한 덩어리 안에서 끝점부터 곧게 이어 걷는다."""
    unvisited_edges = set()
    pix = list(zip(*np.nonzero(sk)))
    for p in pix:
        for q in neighbors(sk, *p):
            unvisited_edges.add(frozenset((p, q)))
    deg = {p: len(neighbors(sk, *p)) for p in pix}

    def walk(start):
        path = [start]
        cur, prev_dir = start, None
        while True:
            opts = [q for q in neighbors(sk, *cur) if frozenset((cur, q)) in unvisited_edges]
            if not opts:
                return path
            if prev_dir is None:
                nxt = opts[0]
            else:
                # 가장 곧게 이어지는 쪽 (방향 변화가 가장 작은 쪽)
                def turn(q):
                    d = (q[0] - cur[0], q[1] - cur[1])
                    return -(d[0] * prev_dir[0] + d[1] * prev_dir[1]) / (math.hypot(*d) * math.hypot(*prev_dir))
                nxt = min(opts, key=turn)
            unvisited_edges.discard(frozenset((cur, nxt)))
            # 대각선으로 이웃한 픽셀끼리의 잔 연결도 지워 같은 곳을 두 번 지나지 않게
            for q in opts:
                if q != nxt and abs(q[0] - nxt[0]) <= 1 and abs(q[1] - nxt[1]) <= 1:
                    unvisited_edges.discard(frozenset((cur, q)))
            # 최근 몇 픽셀의 방향(흔들림 줄이기)
            back = path[-4] if len(path) >= 4 else path[0]
            prev_dir = (nxt[0] - back[0], nxt[1] - back[1]) if nxt != back else prev_dir
            path.append(nxt)
            cur = nxt

    strokes = []
    while unvisited_edges:
        alive = {p for e in unvisited_edges for p in e}
        live_deg = {p: sum(1 for q in neighbors(sk, *p) if frozenset((p, q)) in unvisited_edges) for p in alive}
        ends = [p for p in alive if live_deg[p] == 1]
        start = min(ends or alive, key=lambda p: (p[1] + p[0] * 0.6))  # 왼쪽 위부터
        strokes.append(walk(start))
    return strokes


def rdp(pts, eps):
    if len(pts) < 3:
        return pts
    a, b = np.array(pts[0], float), np.array(pts[-1], float)
    ab = b - a
    L = np.hypot(*ab)
    P = np.array(pts, float)
    if L == 0:
        d = np.hypot(*(P - a).T)
    else:
        d = np.abs(ab[0] * (P[:, 1] - a[1]) - ab[1] * (P[:, 0] - a[0])) / L
    i = int(d.argmax())
    if d[i] > eps:
        return rdp(pts[:i + 1], eps)[:-1] + rdp(pts[i:], eps)
    return [pts[0], pts[-1]]


def smooth(pts, k=2):
    if len(pts) < 2 * k + 1:
        return pts
    P = np.array(pts, float)
    out = P.copy()
    for i in range(k, len(P) - k):
        out[i] = P[i - k:i + k + 1].mean(0)
    return [tuple(p) for p in out]


def convert(path):
    im = Image.open(path).convert("RGBA")
    bg = Image.new("RGBA", im.size, "white")
    im = Image.alpha_composite(bg, im).convert("RGB")
    W0, H0 = im.size
    big = im.resize((W0 * SCALE, H0 * SCALE), Image.BICUBIC)
    rgb = np.array(big)
    ink = rgb.min(-1) < WHITE_CUT
    ink = ndimage.binary_opening(ink, iterations=1)
    colors, hexes = classify(rgb, ink)

    # 종이는 16:9. 그림을 세로에 맞추고 가로 가운데에 둔다(남으면 위아래).
    if W0 / H0 < ASPECT:
        PH, PW = H0, H0 * ASPECT
    else:
        PW, PH = W0, W0 / ASPECT
    ox, oy = (PW - W0) / 2, (PH - H0) / 2

    items = []  # (덩어리 정렬키, 색, 굵기, 점들[원본 픽셀])
    for ci in sorted(set(colors[colors >= 0].tolist())):
        m = colors == ci
        m = ndimage.binary_closing(m, iterations=2) & ink
        lab, n = ndimage.label(m, structure=np.ones((3, 3)))
        dist = ndimage.distance_transform_edt(m)
        for k in range(1, n + 1):
            comp = lab == k
            if comp.sum() < 30 * SCALE:  # 먼지
                continue
            sk = thin(comp)
            width = 2 * float(np.median(dist[sk])) / SCALE  # 원본 픽셀 굵기
            for s in trace(sk):
                if len(s) < 3 * SCALE:  # 세선화 잔가지
                    continue
                pts = [(x / SCALE, y / SCALE) for y, x in s]
                pts = smooth(rdp(smooth(pts), 0.6))
                ys, xs = np.nonzero(comp)
                items.append(((ys.mean() / SCALE, xs.min() / SCALE), hexes[ci], width, pts))

    # 덩어리 순서: 줄(세로 위치를 높이의 1/4 단위로) → 왼쪽부터. 같은 덩어리 획은 붙여서.
    band = H0 / 4
    items.sort(key=lambda it: (round(it[0][0] / band), it[0][1]))

    t = 0
    strokes = []
    for _, color, width, pts in items:
        out = []
        prev = None
        for x, y in pts:
            if prev is not None:
                t += max(1, int(math.hypot(x - prev[0], y - prev[1]) / SPEED))
            out.append({"x": round((x + ox) / PW, 4), "y": round((y + oy) / PH, 4), "t": t})
            prev = (x, y)
        strokes.append({"color": color, "width": round(max(width, 2.0) / PW, 4), "points": out})
        t += PEN_UP
    return {"aspect": round(ASPECT, 4), "strokes": strokes, "texts": [], "text": None}


def preview(letter, out, w=1280):
    h = int(w / letter["aspect"])
    im = Image.new("RGB", (w, h), "white")
    d = ImageDraw.Draw(im)
    for s in letter["strokes"]:
        pts = [(p["x"] * w, p["y"] * h) for p in s["points"]]
        lw = max(1, int(s["width"] * w))
        if len(pts) > 1:
            d.line(pts, fill=s["color"], width=lw, joint="curve")
        for x, y in (pts[0], pts[-1]):
            d.ellipse([x - lw / 2, y - lw / 2, x + lw / 2, y + lw / 2], fill=s["color"])
    im.save(out)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("src")
    ap.add_argument("out")
    ap.add_argument("--preview")
    a = ap.parse_args()
    letter = convert(a.src)
    with open(a.out, "w", encoding="utf-8") as f:
        json.dump(letter, f, ensure_ascii=False, separators=(",", ":"))
    n = sum(len(s["points"]) for s in letter["strokes"])
    dur = letter["strokes"][-1]["points"][-1]["t"] if letter["strokes"] else 0
    print(f"{len(letter['strokes'])} strokes, {n} points, {dur/1000:.1f}s")
    if a.preview:
        preview(letter, a.preview)
