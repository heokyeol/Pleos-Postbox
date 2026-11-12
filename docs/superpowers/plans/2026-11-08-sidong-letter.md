# 시동편지 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 가족이 웹에 그린 낙서가 보낸 뒤 처음 켜는 시동 때(출근길·퇴근길 모두), 출발 전 주차 상태의 Pleos 화면에서 한 획씩 다시 그려지는 차량 앱과 가족용 웹을 만든다.

**Architecture:** 세 부분으로 나눈다. ① Pleos Connect(AAOS) 차량 앱(Kotlin + Jetpack Compose). ② 가족용 정적 웹 스케치북(HTML/JS, GitHub Pages). ③ 둘이 공유하는 Supabase(Postgres + REST). 배달 규칙, 화면 상태, 획 재생은 Android 의존이 없는 `core` 모듈(Kotlin JVM)에 두고 단위 테스트한다. 앱 모듈은 `core`를 가져다 쓴다. 차량 신호(주행 상태, 시동, 문)와 Pleos SDK는 인터페이스 뒤에 두고 Fake로 시연할 수 있게 한다.

**Tech Stack:** Kotlin 2.0, AGP 8.5+, Gradle 8.7+, JDK 17, Compose(BOM), kotlinx-serialization-json, OkHttp 4 + MockWebServer, kotlinx-coroutines-test, JUnit4, zxing core(QR). 웹은 바닐라 JS와 `@supabase/supabase-js@2.45.4`(jsdelivr ESM)를 쓴다.

**Spec:** [시동편지 — 기획서 초안](https://claude.ai/artifact/WsDFM9Rj93CPt7yBp692A7)(Claude 문서). 핵심 결정(2026-11-12 변경): 보낸 뒤 처음 켜는 시동에 배달, 주차(P)에서만 그림 표시, 원터치·음성 답장, 웹은 설치 없이 사용, 대학생 팀 참가.

## 시작 전 준비 (사람이 할 일)

- [ ] JDK 17과 Android Studio를 설치한다. 예: `brew install --cask temurin@17 android-studio`
- [ ] Android Studio > SDK Manager > SDK Update Sites에 `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`(Apple Silicon)을 추가한다. 그다음 `Pleos Connect v2.1.0` Automotive AVD(API 34, RAM 4GB, Cold boot)를 만든다.
- [ ] Supabase 프로젝트를 만든다. 이어서 Task 5의 `supabase/schema.sql`을 실행하고, URL과 anon key를 `local.properties`의 `supabase.url`, `supabase.anonKey`에 넣는다(커밋 금지).
- [ ] Pleos Playground에서 앱을 등록한다. TTS/STT SERVER·HYBRID 모드에 필요한 자격증명을 받는다(Task 8).

## Global Constraints

- `minSdk = 28`, `targetSdk = 34`, `compileSdk = 34`, JDK 17, Gradle 8.0 이상. 출처: 공모전 개발 환경 요구사항.
- applicationId는 `com.sidongletter.car`, 패키지 루트는 `com.sidongletter`.
- 그림(획 재생, 미리보기)은 `DriveState.PARKED`일 때만 그린다. Idling과 Driving에서는 잠금 카드만 보이고 움직임이 없어야 한다.
- 화면 문구는 한 줄 80자 이하, 3줄 이하로 쓴다. 텍스트 대비는 4.5:1 이상, 입력 응답은 0.25초 이내다. 모든 문구는 `res/values/strings.xml`에 둔다.
- 시동 = 차량 시스템 부팅이다. 시동 시각 = `현재 시각 - SystemClock.elapsedRealtime()`. 편지는 `createdAt < 시동 시각`일 때만 보인다. 주행 중 도착한 편지는 다음 시동까지 기다린다.
- 좌표는 0..1로 정규화하고, 편지마다 `aspect`(가로/세로)를 저장한다.
- 비밀값(`local.properties`, 키)은 커밋하지 않는다.
- 공모전 제출 시 APK는 `apk/팀명_시동편지_release.apk` 또는 GitHub Releases에 둔다.

## Review Focus

1. **시동 때 네트워크가 없음** → 이전에 캐시한 편지로 화면을 띄운다. 크래시 없이 "연결되면 새 편지를 가져올게요"를 보인다. (Task 5 `cache_is_used_when_network_fails`)
2. **재생 중 출발** → 즉시 잠금 카드로 바뀐다. 다음 주차 때 그 편지를 처음부터 다시 재생한다. (Task 3 `driving_during_reveal_locks_and_restarts_later`)
3. **밤사이 편지 여러 통** → 오래된 순으로 한 통씩 보여준다. '다음' 버튼과 남은 개수를 표시한다. (Task 3 `multiple_letters_reveal_in_order`)
4. **같은 주행 중 앱 재시작** → 부팅 시각이 같으므로 새 시동이 아니다. 시동 뒤에 온 편지는 다음 시동으로 넘긴다. (Task 2 `letter_sent_after_ignition_waits_for_next`)
5. **글만 있거나 획이 아주 많은 편지** → 획이 0개면 글만 크게 보인다. 웹은 점 20,000개에서 그리기를 막는다. (Task 4 `text_only_letter_has_no_strokes`, Task 6 수동 확인)

---

## File Structure

```
settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/sidongletter/
  letter/Letter.kt            Letter, Stroke, Point, Reply 모델 + LetterJson
  delivery/Delivery.kt        시동 시각 계산, 보여줄 편지 선택 (순수)
  screen/CarScreen.kt         CarScreenState, CarEvent, reduce() (순수)
  replay/StrokeReplay.kt      경과 시간 → 보이는 획 계산 (순수)
  data/LetterRepository.kt    인터페이스
  data/SupabaseLetterRepository.kt  REST 구현
  data/LetterCache.kt         파일 캐시
  data/FamilyStore.kt         가족 코드 저장 (SharedPreferences)
  vehicle/DriveStateSource.kt 인터페이스 + CarUxDriveStateSource + FakeDriveStateSource
  vehicle/DoorSource.kt       인터페이스 + Fake (Pleos Door는 Task 8)
  voice/Speaker.kt, voice/Listener.kt  인터페이스 + Pleos 구현(Task 8)
  pairing/FamilyCode.kt       6자리 코드 생성, QR
  ui/MainActivity.kt, ui/CarViewModel.kt, ui/screens/*.kt
app/src/test/java/com/sidongletter/...  각 순수 모듈 테스트
supabase/schema.sql
web/index.html, web/app.js, web/style.css
README.md
```

---

### Task 1: 프로젝트 뼈대와 편지 모델

**Files:**
- Create: Gradle 파일 일체, `app/src/main/AndroidManifest.xml`, `letter/Letter.kt`, `.gitignore`(Android 표준 + `local.properties`, `*.jks`)
- Test: `app/src/test/java/com/sidongletter/letter/LetterJsonTest.kt`

**Interfaces:**
- Produces:
  - `@Serializable data class Point(val x: Float, val y: Float, val t: Long)` (t = 편지 시작 후 ms)
  - `@Serializable data class Stroke(val color: String, val width: Float, val points: List<Point>)` (color `#RRGGBB`, width는 캔버스 너비 대비 비율)
  - `@Serializable data class Letter(val id: String, val familyCode: String, val senderName: String, val createdAt: Long, val aspect: Float, val strokes: List<Stroke>, val text: String? = null, val openedAt: Long? = null)` (시각은 epoch ms)
  - `@Serializable data class Reply(val letterId: String, val preset: String?, val text: String?)`
  - `object LetterJson { fun decodeLetters(json: String): List<Letter>; fun encodeReply(r: Reply): String }`. JSON 키는 snake_case다(`family_code`, `sender_name`, `created_at`, `opened_at`, `letter_id`). `@SerialName`으로 맞추고, 시각은 Supabase의 ISO-8601 문자열 ↔ epoch ms로 바꾼다.

- [ ] **Step 1:** Android Studio 기본 템플릿 없이 Gradle 파일을 작성한다. Manifest에는 `<uses-feature android:name="android.hardware.type.automotive" android:required="true"/>`와 `INTERNET` 권한을 넣는다.
- [ ] **Step 2: 실패하는 테스트**

```kotlin
@Test fun decodes_supabase_row() {
    val json = """[{"id":"a1","family_code":"K7Q2MX","sender_name":"민준","created_at":"2026-11-08T12:00:00Z",
      "aspect":0.75,"strokes":[{"color":"#FF5A5F","width":0.01,"points":[{"x":0.1,"y":0.2,"t":0}]}],
      "text":"아빠 힘내세요","opened_at":null}]"""
    val l = LetterJson.decodeLetters(json).single()
    assertEquals(1794139200000L, l.createdAt)
    assertEquals("민준", l.senderName)
    assertNull(l.openedAt)
    assertEquals(0.1f, l.strokes[0].points[0].x)
}
@Test fun text_only_letter_decodes_with_empty_strokes()  // "strokes":[] → strokes.isEmpty()
@Test fun unknown_keys_are_ignored()                      // 여분 키가 있어도 실패하지 않음
```

- [ ] **Step 3:** `./gradlew test`로 실행해서 컴파일 실패를 확인한다.
- [ ] **Step 4:** `Letter.kt`를 구현한다. `Json { ignoreUnknownKeys = true }`를 쓴다.
- [ ] **Step 5:** `./gradlew test assembleDebug`를 실행해서 PASS와 BUILD SUCCESSFUL을 확인한다.
- [ ] **Step 6:** 커밋한다. `feat: 프로젝트 뼈대와 편지 모델`

### Task 2: 다음 시동 배달 규칙 (2026-11-12 변경)

**Files:** Create `delivery/Delivery.kt`, Test `delivery/DeliveryTest.kt` (core 모듈)

**Interfaces:**
- Consumes: `Letter`
- Produces:
  - `fun ignitionTime(nowMs: Long, elapsedSinceBootMs: Long): Long` = `nowMs - elapsedSinceBootMs`
  - `fun lettersToReveal(letters: List<Letter>, ignitionAt: Long): List<Letter>`. `openedAt == null && createdAt < ignitionAt`인 편지를 `createdAt` 오름차순으로 돌려준다.

- [ ] **Step 1: 실패하는 테스트**: `ignition_is_now_minus_uptime`, `letters_before_ignition_revealed_oldest_first`, `letter_sent_after_ignition_waits_for_next`, `opened_letters_are_not_revealed_again`
- [ ] **Step 2:** 실패 확인 → 구현 → PASS 확인

### Task 3: 차량 화면 상태 머신

**Files:** Create `screen/CarScreen.kt`, Test `screen/CarScreenTest.kt`

**Interfaces:**
- Consumes: `Letter`, `DriveState`(이 파일에 정의: `enum class DriveState { PARKED, IDLING, DRIVING }`)
- Produces:
  - `sealed interface CarScreenState`: `Empty`, `Waiting(count: Int)`(문 열림·시동 직후 맞이), `Revealing(letter: Letter, remaining: Int)`, `Locked(count: Int)`, `Replying(letter: Letter)`, `Done(lastLetter: Letter)`
  - `sealed interface CarEvent`: `LettersReady(letters: List<Letter>)`, `DriveChanged(state: DriveState)`, `RevealFinished`, `NextLetter`, `ReplyRequested`, `ReplySent`
  - `data class CarModel(val screen: CarScreenState, val queue: List<Letter>, val drive: DriveState)`
  - `fun reduce(model: CarModel, event: CarEvent): CarModel`
- 규칙: `drive != PARKED`이면 큐가 비지 않은 동안 화면은 항상 `Locked(queue.size)`다. PARKED로 돌아오면 큐 맨 앞 편지를 `Revealing`으로 다시 시작한다. 큐가 비어 있으면 `Empty`다. `ReplySent` 또는 `NextLetter`가 오면 그 편지를 큐에서 빼고 다음으로 넘어간다.

- [ ] **Step 1: 실패하는 테스트**

```kotlin
@Test fun parked_with_letters_starts_revealing_first()
@Test fun driving_with_letters_shows_locked_count()          // Locked(2)
@Test fun driving_during_reveal_locks_and_restarts_later()   // Revealing(A) → DRIVING → Locked(…) → PARKED → Revealing(A)
@Test fun multiple_letters_reveal_in_order()                 // A 답장 → Revealing(B, remaining=0)
@Test fun reply_only_from_parked_or_voice_state()            // DRIVING에서 ReplyRequested는 화면을 바꾸지 않음
@Test fun empty_queue_is_empty_screen()
```

- [ ] **Step 2:** 실행해서 실패를 확인한다.
- [ ] **Step 3:** `reduce`를 구현한다(순수 함수, `when` 분기).
- [ ] **Step 4:** 실행해서 PASS를 확인한다.
- [ ] **Step 5:** 커밋한다. `feat: 차량 화면 상태 머신`

### Task 4: 획 재생 계산

**Files:** Create `replay/StrokeReplay.kt`, Test `replay/StrokeReplayTest.kt`

**Interfaces:**
- Produces:
  - `fun replayDurationMs(strokes: List<Stroke>, maxMs: Long = 8_000): Long`. 원래 그린 시간이 8초보다 길면 8초로 압축한다.
  - `fun visibleStrokes(strokes: List<Stroke>, elapsedMs: Long, maxMs: Long = 8_000): List<Stroke>`. 압축 비율을 적용한 `t <= elapsed`까지의 점만 담는다. 점이 없는 획은 뺀다.

- [ ] **Step 1: 실패하는 테스트**

```kotlin
@Test fun nothing_visible_at_zero_except_first_points()
@Test fun everything_visible_after_duration()       // visibleStrokes(s, replayDurationMs(s)) == s
@Test fun long_drawing_is_compressed_to_8s()         // 원래 20초 → duration 8000, 4000ms에 원래 10초 지점까지
@Test fun text_only_letter_has_no_strokes()          // 빈 목록 → duration 0, visible 빈 목록
```

- [ ] **Step 2:** 실행해서 실패를 확인한다.
- [ ] **Step 3:** 구현한다.
- [ ] **Step 4:** 실행해서 PASS를 확인한다.
- [ ] **Step 5:** 커밋한다. `feat: 획 재생 계산`

### Task 5: Supabase 스키마와 데이터 계층

**Files:**
- Create: `supabase/schema.sql`, `data/LetterRepository.kt`, `data/SupabaseLetterRepository.kt`, `data/LetterCache.kt`, `data/FamilyStore.kt`
- Test: `data/SupabaseLetterRepositoryTest.kt` (MockWebServer), `data/LetterCacheTest.kt`

**Interfaces:**
- Consumes: `Letter`, `Reply`, `LetterJson`
- Produces:
  - `interface LetterRepository { suspend fun unopened(familyCode: String): Result<List<Letter>>; suspend fun markOpened(id: String, at: Long): Result<Unit>; suspend fun sendReply(reply: Reply): Result<Unit>; suspend fun registerFamily(code: String): Result<Unit> }`
  - `class SupabaseLetterRepository(baseUrl: String, anonKey: String, client: OkHttpClient, cache: LetterCache) : LetterRepository`. `unopened`는 `GET {baseUrl}/rest/v1/letters?family_code=eq.{code}&opened_at=is.null&order=created_at.asc`를 부르고, 헤더로 `apikey`와 `Authorization: Bearer {anonKey}`를 보낸다. 성공하면 캐시에 저장하고, 실패하면 캐시 값을 `Result.success`로 돌려준다(캐시도 없으면 failure).
  - `class LetterCache(dir: File) { fun save(code: String, letters: List<Letter>); fun load(code: String): List<Letter>? }`
  - `class FamilyStore(prefs: SharedPreferences) { var familyCode: String? }`
- `schema.sql` 테이블:
  - `families(code text primary key, created_at timestamptz default now())`
  - `letters(id uuid primary key default gen_random_uuid(), family_code text references families, sender_name text not null, aspect real not null, strokes jsonb not null default '[]', text text, created_at timestamptz default now(), opened_at timestamptz)`
  - `replies(id uuid primary key default gen_random_uuid(), letter_id uuid references letters, preset text, text text, created_at timestamptz default now())`
  - 시연용 RLS: anon에게 세 테이블의 select/insert를 허용하고, `letters`에는 `opened_at` update만 허용한다. 가족 코드가 사실상 비밀번호라는 한계는 README에 적는다.

- [ ] **Step 1: 실패하는 테스트**

```kotlin
@Test fun unopened_calls_expected_url_and_headers()   // path·query·apikey 헤더 검증
@Test fun cache_is_used_when_network_fails()           // 1회 성공 → 서버 종료 → 같은 목록 반환
@Test fun failure_without_cache_is_failure()
@Test fun markOpened_patches_opened_at()               // PATCH ...letters?id=eq.a1, body {"opened_at":"2026-11-09T21:00:00Z"}
```

- [ ] **Step 2:** 실행해서 실패를 확인한다.
- [ ] **Step 3:** 구현한다(OkHttp 동기 호출을 `Dispatchers.IO`에서 실행).
- [ ] **Step 4:** 실행해서 PASS를 확인한다.
- [ ] **Step 5:** Supabase SQL Editor에서 `schema.sql`을 실행한다. `curl`로 GET을 보내 `[]`가 오는지 확인한다.
- [ ] **Step 6:** 커밋한다. `feat: Supabase 스키마와 편지 저장소`

### Task 6: 가족용 웹 스케치북

**Files:** Create `web/index.html`, `web/app.js`, `web/style.css`, `web/config.js`(URL·anon key. anon key는 공개 전제이며, 공개 저장소면 RLS를 반드시 적용한다)

**Interfaces:**
- Consumes: Task 5 테이블. 삽입하는 JSON 형태는 Task 1 테스트의 행과 같다.
- 화면 흐름:
  1. 가족 코드 입력(`?code=XXXXXX` URL 파라미터로 자동 입력, `localStorage` 저장)
  2. 이름 입력
  3. 캔버스 화면: 세로 3:4(`aspect = 0.75`), 펜 굵기 3종(0.006/0.012/0.024), 색 8개, 지우개(배경색 획), 되돌리기, 글 편지 칸(200자)
  4. '편지 보내기' 버튼("다음 시동 때 도착해요" 안내)
  5. 보낸 편지 목록: 각 편지의 열람 시각과 답장
- 포인터 이벤트(`pointerdown/move/up`)로 점을 기록하고, 좌표는 캔버스 크기로 나눠 정규화한다. `t`는 첫 점 기준 ms다. 점이 20,000개에 이르면 그리기를 막고 안내한다.

- [ ] **Step 1:** 구현한다.
- [ ] **Step 2:** 브라우저 창에서 확인한다(`python3 -m http.server -d web 8080`). 그림 하나를 보내면 Supabase `letters`에 행이 생겨야 한다. 그 행을 `LetterJson.decodeLetters`로 디코딩하는 테스트(`web_payload_decodes`)를 Task 1 테스트 파일에 추가하고 PASS를 확인한다.
- [ ] **Step 3:** 폰 폭(375px)에서 손가락 그리기와 스크롤 잠금을 확인한다.
- [ ] **Step 4:** 커밋한다. `feat: 가족용 웹 스케치북`

### Task 7: 차량 앱 화면과 차량 신호 연결

**Files:**
- Create: `vehicle/DriveStateSource.kt`, `vehicle/DoorSource.kt`, `pairing/FamilyCode.kt`, `ui/MainActivity.kt`, `ui/CarViewModel.kt`, `ui/screens/{PairingScreen,WaitingScreen,RevealScreen,LockedScreen,ReplyScreen,EmptyScreen}.kt`, `res/values/strings.xml`
- Test: `pairing/FamilyCodeTest.kt`, `ui/CarViewModelTest.kt`

**Interfaces:**
- Consumes: Task 2~5 전부
- Produces:
  - `interface DriveStateSource { val state: StateFlow<DriveState> }`. `CarUxDriveStateSource(context)`는 AAOS `CarUxRestrictionsManager`를 쓴다. `isRequiresDistractionOptimization == false`이면 PARKED, 제한이 있고 정지 상태면 IDLING, 그 밖에는 DRIVING이다. 정지 판정 방법은 구현 시 문서로 확인하고, 확인이 안 되면 IDLING을 DRIVING으로 다룬다(안전한 쪽). `FakeDriveStateSource`는 디버그 빌드의 개발자 패널에서 토글한다.
  - `interface DoorSource { val doorOpened: SharedFlow<Unit> }`. Fake만 구현하고, Pleos Door 구현은 Task 8에서 한다.
  - `object FamilyCode { fun generate(random: Random = Random.Default): String }`. 6자, 알파벳 `ABCDEFGHJKMNPQRSTUVWXYZ23456789`(헷갈리는 0/O/1/I/L 제외)를 쓴다.
  - `class CarViewModel(repo, familyStore, drive: DriveStateSource, door: DoorSource, clock: () -> Long, uptime: () -> Long)`. `onAppStart()`에서 `ignitionTime`을 계산하고, 편지를 불러와 `lettersToReveal` 결과로 `LettersReady`를 보낸다. drive 흐름은 `DriveChanged`로 바꾼다. 편지가 열리면 `markOpened`를 부른다.
- 시동 기록: AAOS에서 차량 전원이 켜지면 시스템이 부팅된다. 그래서 `MainActivity`의 콜드 스타트를 시동으로 본다. 디버그 빌드의 개발자 패널에 '시동 다시 켜기'(가상 부팅 시각을 지금으로), '문 열기', '주행/주차' 토글을 둔다(시연 영상용).
- 화면: 가로 레이아웃, 기본 글자 32sp 이상, 버튼 최소 76dp. Reveal은 Compose `Canvas`에 `visibleStrokes`를 그리고, `aspect`에 맞춰 레터박스를 둔다. 답장 프리셋은 4개다: "고마워", "사랑해", "곧 갈게", "♥".

- [ ] **Step 1: 실패하는 테스트**

```kotlin
@Test fun code_is_6_chars_from_safe_alphabet()
@Test fun app_start_at_6am_reveals_overnight_letters()     // FakeRepo 2통, PARKED → Revealing
@Test fun app_restart_same_drive_does_not_show_new_letters() // 시동 뒤 온 편지는 재시작해도 숨김
@Test fun opening_letter_calls_markOpened()
@Test fun offline_start_shows_cached_letters()
```

- [ ] **Step 2:** 실행해서 실패를 확인한다.
- [ ] **Step 3:** ViewModel, Fake 신호원, FamilyCode를 구현한다.
- [ ] **Step 4:** 실행해서 PASS를 확인한다.
- [ ] **Step 5:** 화면과 `CarUxDriveStateSource`를 구현한다. 첫 실행 시 PairingScreen에서 코드와 QR(`https://<pages-url>/?code=...`)을 보여주고 `registerFamily`를 부른다.
- [ ] **Step 6:** Pleos 에뮬레이터로 확인한다. 웹에서 보낸 그림 → '시동 다시 켜기' → 주차 상태 재생 → '주행' 토글 시 즉시 잠금 → '주차' 시 재시작 → 답장 → 웹에 답장 표시. 시나리오 끝까지 동작해야 한다.
- [ ] **Step 7:** 커밋한다. `feat: 차량 화면과 주행 상태 연결`

### Task 8: Pleos SDK 연동 (Door, TTS, STT)

**Files:** Create `vehicle/PleosDoorSource.kt`, `voice/Speaker.kt`, `voice/PleosSpeaker.kt`, `voice/Listener.kt`, `voice/PleosListener.kt`. Modify `settings.gradle.kts`(Pleos maven 저장소), `libs.versions.toml`, `CarViewModel.kt`

**Interfaces:**
- Produces: `interface Speaker { fun speak(text: String) }`, `interface Listener { suspend fun listenOnce(): String? }`
- 확인된 API 이름: Vehicle은 `initialize()`, `getDoor()`, 리스너, `release()`. TTS는 `ai.pleos.playground.tts.TextToSpeech(Context, Mode)`, `initialize()`, `setLocale()`, `speak()`, `OnServerConnectionListener.onConnected()` 후 speak. STT는 `ai.pleos.playground.stt.SpeechToText`, `addListener(ResultListener)`, `request()`. **maven 좌표, 권한 이름, 파라미터, 한국어 locale은 문서에서 확인한 뒤에 쓴다. 추측해서 쓰지 않는다.**

- [ ] **Step 1:** document.pleos.ai에서 SDK Gradle 설정, Door 리스너, TTS/STT `setLocale` 한국어 지원을 확인한다. 결과를 `docs/pleos-sdk-notes.md`에 정리한다.
- [ ] **Step 2:** `PleosDoorSource`를 구현한다. 문이 열리면 `WaitingScreen`의 "오늘 편지가 기다리고 있어요" 맞이가 뜬다.
- [ ] **Step 3:** `PleosSpeaker`를 구현한다. 글 편지는 '읽어주기' 버튼으로 읽는다. 재생 중 출발하면 "나머지는 다음에 차를 세우면 보여드릴게요"를 한 번 말한다(`CarViewModelTest.driving_mid_reveal_speaks_once` 추가).
- [ ] **Step 4:** `PleosListener`로 음성 답장을 만든다. ReplyScreen의 마이크 버튼 → 인식된 문장 → `sendReply(text=…)`.
- [ ] **Step 5:** 에뮬레이터로 확인한다(마이크 접근 설정 필요). 확인 후 커밋한다. `feat: Pleos Door·TTS·STT 연동`

### Task 9: 제출물 준비

**Files:** Create `README.md`, `apk/`(또는 GitHub Release)

- [ ] **Step 1:** README를 쓴다. 앱 이름과 한 줄 소개, 실행 방법(에뮬레이터, `local.properties` 키), 테스트 환경, APK 위치, 가족 코드 보안 한계를 담는다.
- [ ] **Step 2:** 서명된 release APK를 만든다(`./gradlew assembleRelease`, 키스토어는 커밋 금지). 에뮬레이터에 설치해서 시나리오를 다시 확인한다.
- [ ] **Step 3:** 1~2분 시연 영상을 녹화한다. 웹에서 그리기 → 시동 → 주차 재생 → 출발 시 잠금 → 답장 순서다.
- [ ] **Step 4:** 커밋한다. `docs: 제출용 README와 APK`

## 일정

| 날짜 | 끝낼 것 |
| --- | --- |
| 11/9 | 준비, Task 1~4 |
| 11/10~11 | Task 5~6 |
| 11/12~13 | Task 7 |
| 11/14 | Task 9 (11/15 접수: 기획서 + 영상 + GitHub) |
| 11/23 이후 | Task 8, 그리고 PT 준비 |
