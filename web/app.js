// Pleos Postbox 가족용 웹 스케치북.
// 편지 JSON은 core/.../letter/Letter.kt와 같다: 좌표 0..1, t는 첫 점 기준 ms, width·size는 캔버스 너비 대비.
// 캔버스 자체는 투명하고 그 아래 흰 종이(#FFFFFF)가 있다. 지우개 획은 color = ERASE(#00000000)로 저장하며,
// 차량 앱은 이 획을 BlendMode.Clear로 그린다. 글상자(texts)는 획 위에 그린다.

const CODE_RE = /^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$/;
const ASPECT = 16 / 9; // 차 화면 비율(가로/세로). Pleos AVD 화면 사양을 확인하면 맞춘다.
const ERASE = "#00000000";
const MAX_POINTS = 20000;
const MAX_TEXTS = 20;
const INKS = ["#000000", "#E5484D", "#F76B15", "#FFC53D", "#30A46C", "#0090FF", "#8E4EC6", "#E93D82"];
const INK_NAMES = ["검정", "빨강", "주황", "노랑", "초록", "파랑", "보라", "분홍"];
const WIDTHS = [0.004, 0.008, 0.016];      // 펜 굵기
const TEXT_SIZES = [0.032, 0.048, 0.07];   // 같은 버튼으로 고르는 글자 크기
const FONT = (px) => `600 ${px}px "Hyundai Sans Text", "Hyundai Sans Head", "Pretendard Variable", Pretendard, system-ui, sans-serif`;

const $ = (id) => document.getElementById(id);
const store = {
  get: (k) => { try { return localStorage.getItem(k); } catch { return null; } },
  set: (k, v) => { try { localStorage.setItem(k, v); } catch { /* 사생활 보호 모드 등 */ } },
};

// ---------- 데이터 ----------
const config = await import("./config.js").then((m) => m.default).catch(() => null);
const api = config ? remoteApi(config) : localApi();
if (!config) $("preview-tag").hidden = false;

function remoteApi({ supabaseUrl, supabaseAnonKey }) {
  const base = `${supabaseUrl}/rest/v1`;
  const headers = { apikey: supabaseAnonKey, Authorization: `Bearer ${supabaseAnonKey}`, "Content-Type": "application/json" };
  const req = async (path, opts = {}) => {
    const res = await fetch(base + path, { ...opts, headers: { ...headers, ...opts.headers } });
    if (!res.ok) throw new Error(`서버 오류 ${res.status}`);
    return res.status === 204 || res.status === 201 ? null : res.json();
  };
  return {
    familyExists: async (code) => (await req(`/families?code=eq.${code}&select=code`)).length > 0,
    send: (letter) => req("/letters", { method: "POST", headers: { Prefer: "return=minimal" }, body: JSON.stringify(letter) }),
  };
}

function localApi() {
  const KEY = "postbox.local.letters";
  return {
    familyExists: async () => true,
    send: async (letter) => {
      const list = JSON.parse(store.get(KEY) || "[]");
      list.push({ ...letter, id: crypto.randomUUID(), created_at: new Date().toISOString(), opened_at: null });
      store.set(KEY, JSON.stringify(list));
    },
  };
}

// ---------- 처음 설정 ----------
let family = store.get("postbox.code");
let sender = store.get("postbox.name");
const urlCode = new URLSearchParams(location.search).get("code")?.toUpperCase();
if (urlCode && CODE_RE.test(urlCode) && urlCode !== family) family = null;

function openSetup() {
  $("code-input").value = family || (urlCode && CODE_RE.test(urlCode) ? urlCode : "");
  $("name-input").value = sender || "";
  $("setup-error").textContent = "";
  $("sheet").hidden = false;
  document.body.classList.add("setup");
  ($("code-input").value ? $("name-input") : $("code-input")).focus();
}
function closeSetup() {
  $("sheet").hidden = true;
  document.body.classList.remove("setup");
  $("who-initial").textContent = [...sender][0];
  fitBoard();
}

$("setup-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const code = $("code-input").value.trim().toUpperCase();
  const name = $("name-input").value.trim();
  const err = $("setup-error");
  if (!CODE_RE.test(code)) { err.textContent = "가족 코드 6자리를 확인해 주세요."; return; }
  if (!name) { err.textContent = "이름을 적어 주세요."; return; }
  try {
    if (!(await api.familyExists(code))) { err.textContent = "이 코드로 연결된 차가 없어요."; return; }
  } catch { err.textContent = "인터넷 연결을 확인해 주세요."; return; }
  family = code; sender = name;
  store.set("postbox.code", code); store.set("postbox.name", name);
  closeSetup();
});
$("who-btn").addEventListener("click", openSetup);

// ---------- 스케치북 크기: 차 화면 비율로 꽉 차게 ----------
const stage = $("stage");
const board = $("board");
const canvas = $("paper");
const ctx = canvas.getContext("2d");

function fitBoard() {
  // 세로 화면에서는 스케치북이 가로폭을 채우고, 툴바와 함께 가운데 모인다
  const portrait = matchMedia("(orientation: portrait)").matches;
  const sw = stage.getBoundingClientRect().width;
  const sh = portrait ? sw / ASPECT : stage.getBoundingClientRect().height;
  if (!sw || !sh) return;
  const w = Math.floor(Math.min(sw, sh * ASPECT));
  const h = Math.floor(w / ASPECT);
  board.style.width = `${w}px`;
  board.style.height = `${h}px`;
  const dpr = window.devicePixelRatio || 1;
  canvas.width = Math.round(w * dpr);
  canvas.height = Math.round(h * dpr);
  redraw();
}
new ResizeObserver(fitBoard).observe(stage);
document.fonts?.ready.then(() => redraw());

// ---------- 상태 ----------
let strokes = [];
let texts = [];
let history = [];   // 되돌리기용 스냅샷
let startedAt = null;
let ink = INKS[0];
let sizeIdx = 1;
let mode = "draw";  // draw | erase | text
let current = null; // 그리는 중인 획
let editing = null; // 입력 중인 글상자 { box, isNew }
let dragging = null;

const pointCount = () => strokes.reduce((n, s) => n + s.points.length, 0);
const elapsed = () => { const now = performance.now(); if (startedAt === null) startedAt = now; return Math.round(now - startedAt); };
function snapshot() {
  history.push({ strokes: [...strokes], texts: texts.map((t) => ({ ...t })) });
  if (history.length > 100) history.shift();
}

// ---------- 그리기 ----------
function redraw() {
  const w = canvas.width, h = canvas.height;
  ctx.clearRect(0, 0, w, h);
  ctx.lineCap = "round";
  ctx.lineJoin = "round";
  for (const s of strokes) {
    ctx.globalCompositeOperation = s.color === ERASE ? "destination-out" : "source-over";
    ctx.strokeStyle = ctx.fillStyle = s.color === ERASE ? "#000" : s.color;
    ctx.lineWidth = s.width * w;
    const p = s.points;
    ctx.beginPath();
    if (p.length === 1) {
      ctx.arc(p[0].x * w, p[0].y * h, (s.width * w) / 2, 0, Math.PI * 2);
      ctx.fill();
      continue;
    }
    ctx.moveTo(p[0].x * w, p[0].y * h);
    // 점 사이를 중점 곡선으로 이어 손글씨가 매끈하게 보이게 한다
    for (let i = 1; i < p.length - 1; i++) {
      const mx = (p[i].x + p[i + 1].x) / 2, my = (p[i].y + p[i + 1].y) / 2;
      ctx.quadraticCurveTo(p[i].x * w, p[i].y * h, mx * w, my * h);
    }
    const last = p[p.length - 1];
    ctx.lineTo(last.x * w, last.y * h);
    ctx.stroke();
  }
  ctx.globalCompositeOperation = "source-over";

  ctx.textBaseline = "top";
  for (const t of texts) {
    const px = t.size * w;
    ctx.font = FONT(px);
    ctx.fillStyle = t.color;
    const shown = t.text || (editing?.box === t ? " " : "");
    ctx.fillText(shown, t.x * w, t.y * h);
    if (editing?.box === t) {
      // 입력 중인 글상자는 점선 테두리로 표시
      const bw = Math.max(ctx.measureText(shown).width, px * 2);
      const pad = px * 0.25;
      ctx.save();
      ctx.setLineDash([px * 0.15, px * 0.15]);
      ctx.lineWidth = Math.max(1, w * 0.0012);
      ctx.strokeStyle = "rgba(18,21,26,0.45)";
      ctx.strokeRect(t.x * w - pad, t.y * h - pad, bw + pad * 2, px * 1.2 + pad * 2);
      ctx.restore();
    }
  }
  updateChrome();
}

function updateChrome() {
  const empty = strokes.length === 0 && texts.length === 0;
  $("board-hint").classList.toggle("gone", !empty || !!editing);
  $("undo-btn").disabled = history.length === 0;
}

function toNorm(e) {
  const r = canvas.getBoundingClientRect();
  const clamp = (v) => Math.min(1, Math.max(0, v));
  return { x: +clamp((e.clientX - r.left) / r.width).toFixed(4), y: +clamp((e.clientY - r.top) / r.height).toFixed(4) };
}

/** 글상자가 종이 오른쪽·아래로 넘치지 않게 안쪽으로 당긴다 */
function keepInside(box) {
  const w = canvas.width, h = canvas.height;
  ctx.font = FONT(box.size * w);
  const bw = ctx.measureText(box.text).width / w;
  box.x = +Math.max(0, Math.min(box.x, 0.99 - bw)).toFixed(4);
  box.y = +Math.max(0, Math.min(box.y, 0.99 - (box.size * w * 1.2) / h)).toFixed(4);
}

/** 화면 좌표 위에 있는 글상자 (위에 그린 것 우선) */
function textAt(pt) {
  const w = canvas.width, h = canvas.height;
  for (let i = texts.length - 1; i >= 0; i--) {
    const t = texts[i];
    const px = t.size * w;
    ctx.font = FONT(px);
    const bw = ctx.measureText(t.text).width;
    const x = pt.x * w, y = pt.y * h, pad = px * 0.3;
    if (x >= t.x * w - pad && x <= t.x * w + bw + pad && y >= t.y * h - pad && y <= t.y * h + px * 1.2 + pad) return t;
  }
  return null;
}

canvas.addEventListener("pointerdown", (e) => {
  const pt = toNorm(e);
  if (editing) { commitText(); return; }

  if (mode === "text") {
    const hit = textAt(pt);
    if (hit) {
      canvas.setPointerCapture(e.pointerId);
      dragging = { box: hit, from: pt, x0: hit.x, y0: hit.y, moved: false };
      return;
    }
    if (texts.length >= MAX_TEXTS) { toast("글상자는 20개까지 넣을 수 있어요."); return; }
    const size = TEXT_SIZES[sizeIdx];
    const box = { x: pt.x, y: Math.max(0, +(pt.y - (size * ASPECT) * 0.6).toFixed(4)), text: "", color: ink, size, t: elapsed() };
    startEditing(box, true);
    return;
  }

  if (mode === "erase") {
    const hit = textAt(pt);
    if (hit) { snapshot(); texts = texts.filter((t) => t !== hit); redraw(); return; }
  }
  if (pointCount() >= MAX_POINTS) { toast("종이가 꽉 찼어요. 이대로 보내 주세요."); return; }
  canvas.setPointerCapture(e.pointerId);
  snapshot();
  current = {
    color: mode === "erase" ? ERASE : ink,
    width: mode === "erase" ? WIDTHS[2] * 2 : WIDTHS[sizeIdx],
    points: [{ ...pt, t: elapsed() }],
  };
  strokes.push(current);
  redraw();
});

canvas.addEventListener("pointermove", (e) => {
  if (dragging) {
    const pt = toNorm(e);
    const dx = pt.x - dragging.from.x, dy = pt.y - dragging.from.y;
    if (!dragging.moved && Math.hypot(dx, dy) < 0.01) return;
    if (!dragging.moved) { snapshot(); dragging.moved = true; }
    dragging.box.x = dragging.x0 + dx;
    dragging.box.y = dragging.y0 + dy;
    keepInside(dragging.box);
    redraw();
    return;
  }
  if (!current) return;
  if (pointCount() >= MAX_POINTS) { current = null; toast("종이가 꽉 찼어요. 이대로 보내 주세요."); return; }
  for (const ev of e.getCoalescedEvents?.() ?? [e]) current.points.push({ ...toNorm(ev), t: elapsed() });
  redraw();
});

function endPointer() {
  if (dragging && !dragging.moved) startEditing(dragging.box, false); // 살짝 누르면 고치기
  dragging = null;
  current = null;
}
canvas.addEventListener("pointerup", endPointer);
canvas.addEventListener("pointercancel", () => { dragging = null; current = null; });

// ---------- 글상자 ----------
function startEditing(box, isNew) {
  if (isNew) texts.push(box);
  else snapshot();
  editing = { box, isNew, before: box.text };
  $("text-bar").hidden = false;
  const input = $("text-input");
  input.value = box.text;
  input.style.color = box.color;
  input.focus();
  redraw();
}
function commitText() {
  if (!editing) return;
  const { box, isNew } = editing;
  const value = $("text-input").value.trim();
  if (isNew) {
    texts = texts.filter((t) => t !== box);
    if (value) { snapshot(); box.text = value; texts.push(box); }
  } else if (!value) {
    texts = texts.filter((t) => t !== box);
  } else if (value === editing.before) {
    history.pop(); // 바뀐 게 없으면 되돌리기 기록도 남기지 않는다
  } else {
    box.text = value;
  }
  keepInside(box);
  editing = null;
  $("text-bar").hidden = true;
  $("text-input").blur();
  redraw();
}
$("text-input").addEventListener("input", () => {
  if (!editing) return;
  editing.box.text = $("text-input").value;
  keepInside(editing.box);
  redraw();
});
$("text-input").addEventListener("keydown", (e) => { if (e.key === "Escape") { $("text-input").value = editing?.before ?? ""; commitText(); } });
$("text-bar").addEventListener("submit", (e) => { e.preventDefault(); commitText(); });

// ---------- 도구 ----------
function pressOnly(container, btn) {
  for (const b of container.children) b.setAttribute("aria-pressed", String(b === btn));
}
function setMode(m) {
  mode = m;
  $("text-btn").setAttribute("aria-pressed", String(m === "text"));
  document.body.classList.toggle("mode-text", m === "text");
}

INKS.forEach((c, i) => {
  const b = document.createElement("button");
  b.type = "button"; b.className = "ink"; b.style.setProperty("--c", c);
  b.setAttribute("aria-label", INK_NAMES[i]);
  b.setAttribute("aria-pressed", String(c === ink));
  b.addEventListener("click", () => {
    ink = c;
    if (mode === "erase") setMode("draw");
    if (editing) { editing.box.color = c; $("text-input").style.color = c; redraw(); }
    pressOnly($("inks"), b);
  });
  $("inks").append(b);
});
{
  const b = document.createElement("button");
  b.type = "button"; b.className = "ink erase";
  b.setAttribute("aria-label", "지우개");
  b.setAttribute("aria-pressed", "false");
  b.addEventListener("click", () => { commitText(); setMode("erase"); pressOnly($("inks"), b); });
  $("inks").append(b);
}
WIDTHS.forEach((_, i) => {
  const b = document.createElement("button");
  b.type = "button"; b.className = "width";
  b.setAttribute("aria-label", ["가늘게·작게", "보통", "굵게·크게"][i]);
  b.setAttribute("aria-pressed", String(i === sizeIdx));
  const dot = document.createElement("span");
  dot.style.width = dot.style.height = `${[4, 8, 14][i]}px`;
  b.append(dot);
  b.addEventListener("click", () => {
    sizeIdx = i;
    if (editing) { editing.box.size = TEXT_SIZES[i]; redraw(); }
    pressOnly($("widths"), b);
  });
  $("widths").append(b);
});

$("text-btn").addEventListener("click", () => {
  commitText();
  if (mode === "text") { setMode("draw"); }
  else {
    setMode("text");
    // 지우개를 쓰던 중이면 고른 색으로 돌아간다
    pressOnly($("inks"), $("inks").children[INKS.indexOf(ink)]);
    toast("글을 넣을 곳을 눌러 주세요");
  }
});

$("undo-btn").addEventListener("click", () => {
  if (editing) { commitText(); }
  const prev = history.pop();
  if (!prev) return;
  strokes = prev.strokes; texts = prev.texts;
  if (strokes.length === 0 && texts.length === 0) startedAt = null;
  redraw();
});

// ---------- 알림 ----------
let toastTimer;
function toast(msg) {
  const t = $("toast");
  t.textContent = msg;
  t.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove("show"), 2000);
}

// ---------- 보내기 ----------
const reduceMotion = () => matchMedia("(prefers-reduced-motion: reduce)").matches;
function leaveAnimation() {
  if (reduceMotion()) return Promise.resolve();
  canvas.classList.add("leaving");
  return new Promise((ok) => { canvas.addEventListener("animationend", ok, { once: true }); setTimeout(ok, 800); });
}

$("send-btn").addEventListener("click", async () => {
  commitText();
  if (strokes.length === 0 && texts.length === 0) { toast("먼저 그림이나 글을 넣어 주세요."); return; }
  const btn = $("send-btn");
  btn.disabled = true;
  try {
    await api.send({ family_code: family, sender_name: sender, aspect: ASPECT, strokes, texts, text: null });
    await leaveAnimation();
    strokes = []; texts = []; history = []; startedAt = null;
    canvas.classList.remove("leaving");
    setMode("draw");
    redraw();
    toast("보냈어요. 다음 시동 때 도착해요.");
  } catch {
    toast("보내지 못했어요. 인터넷 연결을 확인해 주세요.");
  } finally {
    btn.disabled = false;
  }
});

// ---------- 시작 ----------
if (family && sender) closeSetup(); else openSetup();
fitBoard();
