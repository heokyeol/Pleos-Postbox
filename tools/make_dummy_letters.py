"""차량 앱 더미 편지(app/src/main/assets/letters.json)를 만든다.

그림은 가족 역할로 웹 스케치북(미리보기 모드, tools/dev_server.py)에서 직접 그려 보낸 편지다.
보낸 편지는 tools/dummy/web_letters.json에 보낸 순서대로 쌓여 있고,
여기서 각 편지에 그럴듯한 받은 시각·읽은 시각을 붙여 letters.json을 만든다.
opened_at이 None인 편지가 '새 편지'(다음 시동 때 도착)다.

실행: python3 tools/make_dummy_letters.py
"""
import json, os

HERE = os.path.dirname(__file__)
SRC = os.path.join(HERE, "dummy", "web_letters.json")
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "letters.json")
FAMILY = "K7Q2MX"
TZ = "+09:00"

# (web_letters.json 순번, 받은 시각, 읽은 시각 또는 None) — 2026년 11월
# 퇴근·출근길 시동 때 읽은 것으로 맞춘다. 읽은 시각이 None이면 새 편지.
SCHEDULE = [
    (3, "11-09T15:40", "11-09T18:30"),  # 민진: 오늘 상장 받았어요
    (2, "11-11T16:30", "11-11T18:40"),  # 민준: 소풍 가고 싶어요
    (8, "11-11T20:40", "11-12T08:10"),  # 민진: 다음 주 생신 축하드려요
    (5, "11-12T17:20", "11-12T18:35"),  # 민진: 이번 주말에는 치킨
    (1, "11-12T18:10", "11-12T18:35"),  # 아내: 추운데 조심히 와, 저녁은 고등어김치찌개야
    (0, "11-12T21:30", "11-13T08:10"),  # 민준: 아빠 사랑해요
    (10, "11-13T17:40", None),          # 민준: 아빠 안전운전 하세요 (새 편지)
    (9, "11-13T18:25", None),           # 민준: 아빠 언제 와요 (새 편지)
    (4, "11-14T07:50", "11-14T08:05"),  # 아내: 아침부터 잔소리해서 미안해
    (6, "11-14T19:20", "11-14T19:40"),  # 민준: 가족 그림
]

# 보냈지만 더미에 넣지 않는 편지 (web_letters.json 순번)
EXCLUDE = {
    7,  # 아내: 올 때 애들 요거트 좀
}


def stamp(s):
    return f"2026-{s}:00{TZ}" if s else None


def main():
    with open(SRC, encoding="utf-8") as f:
        sent = json.load(f)
    used = [i for i, *_ in SCHEDULE]
    assert len(used) == len(set(used)), "SCHEDULE에 같은 편지가 두 번 들어 있어요"
    assert sorted(used + list(EXCLUDE)) == list(range(len(sent))), \
        "web_letters.json의 모든 편지를 SCHEDULE에 넣거나 EXCLUDE에 적어 주세요"
    letters = []
    for n, (i, created, opened) in enumerate(SCHEDULE, 1):
        l = sent[i]
        letters.append({
            "id": f"dummy-{n:02d}", "family_code": FAMILY, "sender_name": l["sender_name"],
            "created_at": stamp(created), "opened_at": stamp(opened),
            "aspect": l["aspect"], "strokes": l["strokes"], "texts": l.get("texts", []), "text": l.get("text"),
        })
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(letters, f, ensure_ascii=False, separators=(",", ":"))
    new = sum(l["opened_at"] is None for l in letters)
    print(f"{os.path.relpath(OUT)}: 편지 {len(letters)}통(새 편지 {new}통)")


if __name__ == "__main__":
    main()
