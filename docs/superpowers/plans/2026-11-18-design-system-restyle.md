# 디자인 시스템 적용 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `DESIGN.md`의 토큰(색·글꼴·모양)을 앱 테마로 옮기고, 승인된 시안(B안)대로 모든 화면의 레이아웃을 바꾼다. 기능과 데이터 동작은 그대로 둔다.

**Architecture:** 토큰은 `ui/theme`에 한 번만 정의하고 Material3 테마(`lightColorScheme`, `Typography`, `Shapes`)로 연결한다. 시안의 반복 요소(알약 버튼, 네이비 요약 카드, 비율 막대, 상태 태그, 크림 배너, 칩, 네 칸 입력 등)는 `ui/components`의 작은 컴포저블로 만든다. 화면은 이 부품만 조합한다. 비율·날짜 묶음 같은 표시용 계산은 `ui/model`의 순수 함수로 두고 단위 테스트한다.

**Tech Stack:** 기존 그대로(Compose BOM 2026.09.00, Material3 1.4.0). 추가 의존성은 `androidx.compose.material:material-icons-core`(BOM 관리, 1.7.8)뿐이다. 글꼴은 Pretendard OTF(Light, Regular)를 리소스로 넣는다.

**Spec:** `docs/superpowers/specs/2026-11-18-design-system-restyle-design.md` (+ 루트 `DESIGN.md`, 시안 `.superpowers/brainstorm/*/content/main-direction.html`의 B, `screens-b.html`)

## Global Constraints

- **색은 정확히 이 값을 쓴다:**
  - primary `#533afd`, primary-deep `#4434d4`, primary-press `#2e2b8c`, subdued `#b9b9f9`
  - navy(brand-dark-900) `#1c1e54`
  - ink `#0d253d`, ink-secondary `#273951`, ink-mute `#64748d`
  - canvas `#ffffff`, canvas-soft `#f6f9fc`, cream `#f5e9d4`, lemon `#9b6829`
  - hairline `#e3e8ee`, hairline-input `#a8c3de`, ruby `#ea2261`
- **모서리:** 입력 6dp, 작은 카드 8dp, 카드 12dp. 버튼·태그·칩은 완전 둥금(`RoundedCornerShape(50)`).
- **간격:** 2·4·8·12·16·24·32dp만 쓴다.
- **글꼴:** Pretendard만 쓴다.
  - 큰 제목과 금액은 Light(300). 본문·라벨·버튼은 Regular(400).
  - 금액 텍스트는 모두 `fontFeatureSettings = "tnum"`.
- **밝은 테마만 쓴다.** `dynamicColorScheme`과 다크 모드 분기를 없앤다.
- **동작은 바꾸지 않는다.** ViewModel의 공개 API는 이 계획이 추가하는 필드 외에는 유지하고, 화면 문구는 스펙에 적힌 것 외에는 그대로 둔다.
- 기존 테스트 131개는 계속 통과해야 한다.
- **커밋할 때는 이 작업에서 바꾼 경로만 `git add <경로>`로 올린다.** `git add -A`는 쓰지 않는다. 사용자가 같은 폴더에서 동시에 작업하고, 저장소는 공개 상태다.
- 커밋 메시지 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`를 붙인다.
- **테스트 방법:**
  - 순수 함수와 ViewModel 필드는 단위 테스트한다.
  - 테마·부품·화면 배치는 시각 결과물이므로 `./gradlew :app:assembleDebug`와 에뮬레이터(`Medium_Phone_API_35`) 스크린샷으로 확인한다.

## Review Focus

1. **긴 가맹점명이나 별칭:** 한 줄 말줄임(…)으로 잘려야 하고, 오른쪽 금액을 밀어내면 안 된다. → Task 9에서 긴 이름으로 확인
2. **카드나 결제가 많을 때:** 목록이 하단 탭이나 하단 버튼 뒤에 가려지지 않아야 한다(패딩·인셋 처리). → Task 9
3. **결제 합계가 0원이거나 음수인 달:** 비율 막대가 비고 "NaN%"나 음수 %가 보이면 안 된다. → Task 2 `recognizedPercent_nonPositivePayment_null`
4. **입력 화면에서 키보드가 열릴 때:** 입력칸과 저장 버튼이 키보드에 가려지면 안 된다(`imePadding`). → Task 9
5. **글자 크기를 크게 설정한 폰(1.3배):** 금액이 잘리지 않고 줄바꿈되어야 한다. → Task 9 (`adb shell settings put system font_scale 1.3`)

---

### Task 1: 정리, 글꼴, 토큰, 테마

**Files:**
- Modify: `.gitignore`(`.idea/` 추가), `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/java/com/jstensix/cardspending/ui/theme/Theme.kt`, `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/font/pretendard_light.otf`, `app/src/main/res/font/pretendard_regular.otf`, `third_party/pretendard/OFL.txt`, `app/src/main/java/com/jstensix/cardspending/ui/theme/Tokens.kt`
- Git: `git rm -r --cached .idea`. 사용자가 스테이징한 `app/DESIGN.md → DESIGN.md` 이동은 그대로 함께 커밋한다.

**Interfaces:**
- Produces:
```kotlin
object Brand {  // DESIGN.md colors
    val Primary: Color; val PrimaryDeep: Color; val PrimaryPress: Color; val Subdued: Color; val Navy: Color
    val Ink: Color; val InkSecondary: Color; val InkMute: Color
    val Canvas: Color; val CanvasSoft: Color; val Cream: Color; val Lemon: Color
    val Hairline: Color; val HairlineInput: Color; val Ruby: Color
}
val Pretendard: FontFamily                 // Light(300) + Regular(400)
object BrandText {                         // TextStyles, all Pretendard
    val display: TextStyle      // 32sp, Light, lineHeight 1.1, letterSpacing -0.64sp, tnum
    val amountLarge: TextStyle  // 28sp, Light, -0.56sp, tnum
    val amount: TextStyle       // 20sp, Light, -0.4sp, tnum
    val amountSmall: TextStyle  // 15sp, Regular, -0.42sp, tnum
    val title: TextStyle        // 18sp, Regular
    val body: TextStyle         // 15sp, Regular, lineHeight 1.4
    val label: TextStyle        // 13sp, Regular
    val caption: TextStyle      // 12sp, Regular, color InkMute 는 호출부에서
    val eyebrow: TextStyle      // 11sp, Regular, letterSpacing 0.1sp
    val button: TextStyle       // 16sp, Regular
}
@Composable fun CardSpendingTheme(content: @Composable () -> Unit)   // light only
```
Material3 연결: `lightColorScheme(primary=Primary, onPrimary=White, primaryContainer=Subdued, onPrimaryContainer=PrimaryDeep, secondary=Navy, onSecondary=White, background=CanvasSoft, onBackground=Ink, surface=Canvas, onSurface=Ink, surfaceVariant=CanvasSoft, onSurfaceVariant=InkMute, surfaceContainer*=Canvas, outline=HairlineInput, outlineVariant=Hairline, error=Ruby)`. `Typography`의 모든 슬롯에 `fontFamily = Pretendard`. `Shapes(extraSmall=6.dp, small=8.dp, medium=12.dp, large=16.dp, extraLarge=28.dp)`.
`themes.xml`: 상태바/내비게이션바 밝은 배경에 어두운 아이콘(`android:windowLightStatusBar=true`, `android:windowLightNavigationBar=true`, 배경 `#f6f9fc`).

- [ ] **Step 1:** 글꼴을 받아 넣고 라이선스를 복사
```bash
curl -L -o app/src/main/res/font/pretendard_light.otf https://raw.githubusercontent.com/orioncactus/pretendard/v1.3.9/packages/pretendard/dist/public/static/Pretendard-Light.otf
curl -L -o app/src/main/res/font/pretendard_regular.otf https://raw.githubusercontent.com/orioncactus/pretendard/v1.3.9/packages/pretendard/dist/public/static/Pretendard-Regular.otf
curl -L -o third_party/pretendard/OFL.txt https://raw.githubusercontent.com/orioncactus/pretendard/v1.3.9/LICENSE
```
Expected: 두 OTF가 각각 약 1.6MB, `file` 결과가 OpenType
- [ ] **Step 2:** `material-icons-core`를 catalog(`androidx-compose-material-icons-core`, BOM 관리)와 앱 의존성에 추가하고, `Tokens.kt`와 `Theme.kt`를 위 인터페이스대로 작성
- [ ] **Step 3:** `.gitignore`에 `.idea/`를 추가하고 `git rm -r --cached .idea`
- [ ] **Step 4:** 확인 — `./gradlew :app:testDebugUnitTest :app:assembleDebug` / Expected: BUILD SUCCESSFUL, 131 tests pass
- [ ] **Step 5:** Commit (명시 경로 + 사용자의 DESIGN.md 이동) — `style: DESIGN.md 토큰과 Pretendard 글꼴로 테마 교체`

---

### Task 2: 표시용 계산 (비율, 날짜 묶음)

**Files:**
- Create: `app/src/main/java/com/jstensix/cardspending/ui/model/Presentation.kt`
- Test: `app/src/test/java/com/jstensix/cardspending/ui/model/PresentationTest.kt`

**Interfaces:**
- Produces:
```kotlin
fun recognizedRatio(payment: Long, recognized: Long): Float?   // payment <= 0 → null; else (recognized/payment) clamp 0f..1f
fun recognizedPercent(payment: Long, recognized: Long): Int?   // floor(ratio * 100); payment <= 0 → null
fun dayLabel(date: LocalDate): String                          // "11월 18일 (수)" (요일: 월화수목금토일)
data class DayGroup<T>(val date: LocalDate, val label: String, val items: List<T>)
fun <T> groupByDay(items: List<T>, dateOf: (T) -> LocalDate): List<DayGroup<T>>  // 입력 순서 유지, 같은 날짜끼리 묶음
```

- [ ] **Step 1: 실패하는 테스트**
```kotlin
class PresentationTest {
    @Test fun recognizedRatio_normal() { assertEquals(481200f / 523400f, recognizedRatio(523400, 481200)!!, 0.0001f) }
    @Test fun recognizedRatio_clamped() { assertEquals(1f, recognizedRatio(1000, 1500)); assertEquals(0f, recognizedRatio(1000, -500)) }
    @Test fun recognizedPercent_floors() { assertEquals(93, recognizedPercent(643400, 601200)) }
    @Test fun recognizedPercent_nonPositivePayment_null() { assertNull(recognizedPercent(0, 0)); assertNull(recognizedPercent(-20000, -20000)); assertNull(recognizedRatio(-1, -1)) }
    @Test fun dayLabel_koreanWeekday() {
        assertEquals("11월 18일 (수)", dayLabel(LocalDate.of(2026, 11, 18)))
        assertEquals("11월 17일 (화)", dayLabel(LocalDate.of(2026, 11, 17)))
    }
    @Test fun groupByDay_keepsOrder() {
        val d18 = LocalDate.of(2026, 11, 18); val d17 = LocalDate.of(2026, 11, 17)
        val groups = groupByDay(listOf("a" to d18, "b" to d18, "c" to d17)) { it.second }
        assertEquals(listOf(d18, d17), groups.map { it.date })
        assertEquals(listOf("a", "b"), groups[0].items.map { it.first })
        assertEquals("11월 17일 (화)", groups[1].label)
    }
}
```
- [ ] **Step 2:** 실패 확인 — `--tests "*PresentationTest"` / Expected: 컴파일 실패
- [ ] **Step 3:** 구현
- [ ] **Step 4:** 통과 확인
- [ ] **Step 5:** Commit — `feat: 실적인정 비율과 날짜 묶음 표시 계산`

---

### Task 3: 공용 부품

**Files:**
- Create: `app/src/main/java/com/jstensix/cardspending/ui/components/` 아래 `Buttons.kt`, `Summary.kt`, `Tags.kt`, `Inputs.kt`, `Bars.kt`; `app/src/main/res/drawable/ic_card.xml`(Material `credit_card` 경로, 24dp)
- Modify: `app/src/main/java/com/jstensix/cardspending/ui/IssuerLogo.kt`(로고 주변 1dp hairline 테두리 옵션)

**Interfaces:**
- Produces (모두 `@Composable`):
```kotlin
enum class PillStyle { Primary, Secondary }
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: PillStyle = PillStyle.Primary, enabled: Boolean = true)
    // 최소 높이 44dp, 좌우 16dp 패딩, Primary=채운 인디고(누름 primary-press), Secondary=흰 바탕+인디고 1dp 테두리
fun RatioBar(ratio: Float?, onDark: Boolean, modifier: Modifier = Modifier)   // 높이 6dp 알약; ratio null이면 빈 트랙
fun NavySummaryCard(eyebrow: String, amount: Long, caption: String?, ratio: Float?, modifier: Modifier = Modifier, amountStyle: TextStyle = BrandText.display)
    // navy 바탕, 12dp, 패딩 18dp, eyebrow·caption 색 Subdued, 금액 흰색
enum class TagKind { Neutral, Excluded, Manual, Accent }   // Neutral=soft/mute, Excluded=cream/lemon, Manual=subdued/primary-deep, Accent=subdued/primary-deep
fun StatusTag(text: String, kind: TagKind)
fun CreamBanner(text: String, onClick: () -> Unit, modifier: Modifier = Modifier)   // cream 바탕 8dp, 오른쪽 "›"
fun PillChip(text: String, selected: Boolean, onClick: () -> Unit)                 // 선택: subdued 바탕+primary-deep 글자, 해제: 흰 바탕+hairline 테두리
fun SegmentedToggle(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier)  // soft 트랙, 선택 칸 흰색+그림자
fun LogoChoiceCard(issuer: Issuer, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)  // 선택 시 primary 2dp 테두리
fun DigitBoxes(value: String, onValueChange: (String) -> Unit, highlighted: Set<Int> = setOf(0, 2), modifier: Modifier = Modifier)
    // BasicTextField(숫자 키패드, 4자 제한) + 44x52dp 칸 4개, highlighted 칸은 primary 테두리 + #f3f1ff 바탕
fun BrandTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {})
fun MonthSwitcher(label: String, onPrevious: () -> Unit, onNext: () -> Unit)        // soft 바탕 알약, ‹ label ›
fun SectionLabel(text: String)                                                      // 12sp Regular ink-secondary, 위 14dp
fun WhiteCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit)
    // 흰 바탕, 12dp, hairline 1dp 테두리, 1dp 그림자, 패딩 14dp
```
- [ ] **Step 1:** 위 부품 구현(화면은 아직 바꾸지 않음)
- [ ] **Step 2:** 확인 — `./gradlew :app:assembleDebug` / Expected: BUILD SUCCESSFUL
- [ ] **Step 3:** Commit — `feat: DESIGN.md 공용 UI 부품`

---

### Task 4: 하단 탭과 메인 화면(B안)

**Files:**
- Modify: `ui/AppNavHost.kt`, `ui/main/MainScreen.kt`, `ui/main/MainViewModel.kt`
- Test: `ui/main/MainViewModelTest.kt`

**Interfaces:**
- Consumes: Task 2 `recognizedRatio`, `recognizedPercent`; Task 3 부품
- Produces:
```kotlin
data class CardRow(..., val excludedCount: Int = 0)   // 기존 필드 뒤에 추가: 이 달 이 카드의 미인정 승인 건수
data class MainUiState(..., val totalPayment: Long = 0, val totalRecognized: Long = 0)   // rows 합
```
하단 탭: 홈(`MainRoute`, Icons.Outlined.Home), 카드(`CardsRoute`, `ic_card`), 알림함(`UnparsedRoute`, Icons.Outlined.MailOutline, 인식 실패 건수 > 0이면 `Badge`), 설정(`SettingsRoute`, Icons.Outlined.Settings). 탭 화면에서만 `NavigationBar`를 보이고, 탭 이동은 `popUpTo(MainRoute) { saveState = true }; launchSingleTop = true; restoreState = true`. 선택 색 primary, 표시줄 indicator는 subdued.
메인 화면:
- 맨 위: `BrandTopBar("카드 실적")` + `MonthSwitcher`
- 그 아래: `NavySummaryCard("이번 달 실적인정 합계", totalRecognized, "결제 {formatWon(totalPayment)} 중 {percent}%", ratio)`. percent가 null이면 caption은 "결제 {formatWon(totalPayment)}"
- 경고: 모두 `CreamBanner`
- 카드마다 `WhiteCard`:
  - 첫 줄: 로고 36dp, 별칭(말줄임), 마스크, 미인정 건수가 1 이상이면 `StatusTag("미인정 N건", Excluded)`
  - 둘째 줄: 실적인정 `BrandText.amount`, 오른쪽 "/ 결제 N원"
  - 셋째 줄: `RatioBar(onDark=false)`
- 빈 상태: "등록된 카드가 없어요" + `PillButton("카드 등록")`
- 상단의 '카드 관리'·'설정' 글자 버튼은 없앤다(탭으로 대체).

- [ ] **Step 1: 실패하는 테스트** (`MainViewModelTest`에 추가)
```kotlin
@Test fun totalsAndExcludedCount() {
    // 기존 rows_showMonthTotals와 같은 준비({TAX} 카드, 11월 10,000 편의점 + 50,000 국세)
    // → state.totalPayment == 60000, state.totalRecognized == 10000, rows.single().excludedCount == 1
}
```
- [ ] **Step 2:** 실패 확인 — `--tests "*MainViewModelTest"`
- [ ] **Step 3:** ViewModel 구현(카드별 미인정 건수 = `card.owns(tx) && kind == APPROVAL && !recognize(...).recognized`), 화면·탭 구현
- [ ] **Step 4:** 통과 확인 + `./gradlew :app:testDebugUnitTest :app:assembleDebug`
- [ ] **Step 5:** 에뮬레이터 확인 — 설치 후 카드 2장, 결제 몇 건(직접 추가 또는 `cmd notification post`)으로 메인 스크린샷을 찍어 시안 B와 비교
- [ ] **Step 6:** Commit — `style: 하단 탭과 B안 메인 화면`

---

### Task 5: 카드 결제 내역

**Files:**
- Modify: `ui/detail/CardDetailViewModel.kt`, `ui/detail/CardDetailScreen.kt`
- Test: `ui/detail/CardDetailViewModelTest.kt`

**Interfaces:**
- Produces: `CardDetailState`에 `val days: List<DayGroup<TxItem>> = emptyList()`를 추가한다(`items`는 유지). 날짜 순서는 최신부터.
- 화면 구성:
  - 맨 위: `BrandTopBar(별칭, onBack)`. 오른쪽 actions에는 월 이름만 담은 soft 알약을 둔다(화살표 없음, 이 화면은 월 이동이 없는 기존 동작을 유지한다).
  - 요약: `NavySummaryCard("실적인정", recognized, "결제 N원 · 미인정 M원", ratio, amountStyle = BrandText.amountLarge)`. M = payment − recognized.
  - 목록: 날짜 제목(`BrandText.label`, ink-mute) 아래에 줄을 둔다.
    - 줄 왼쪽: 가맹점(말줄임), 그 아래 "HH:mm · 일시불|할부"
    - 줄 오른쪽: 금액(`amountSmall`), 그 아래 `StatusTag`. 인정=Neutral "인정", 항목 미인정=Excluded, 직접 지정=Manual
  - 줄을 누르면 `ModalBottomSheet`가 열린다.
    - 제목: 가맹점
    - `SegmentedToggle(["규칙대로","인정","미인정"])`: 현재값은 `manualRecognition` null→0, true→1, false→2
    - 원문 `rawText`가 있으면 soft 상자에 보여준다.
    - 삭제(ink-mute 글자 버튼)
  - 하단 고정: `PillButton("＋ 결제 추가")`와 `PillButton("취소 추가", style = Secondary)`. 목록은 버튼 높이만큼 아래 패딩을 둔다.

- [ ] **Step 1: 실패하는 테스트**
```kotlin
@Test fun days_groupedNewestFirst() {
    // 기존 준비(11/4 국세, 11/3 편의점) → state.days.map { it.label } == listOf("11월 4일 (수)", "11월 3일 (화)")
}
```
- [ ] **Step 2~4:** 실패 확인 → 구현 → 통과 + 빌드
- [ ] **Step 5:** Commit — `style: 카드 결제 내역 날짜 묶음과 아래 시트`

---

### Task 6: 결제 직접 추가

**Files:**
- Modify: `ui/entry/ManualEntryScreen.kt` (ViewModel은 그대로)

화면 구성:
- 맨 위: `BrandTopBar("결제 직접 추가" | "취소 직접 추가", onBack)`
- 인식 실패 원문(`rawText`)이 있으면 soft 상자에 보여준다.
- `SegmentedToggle(["승인","취소"])`
- '카드' 라벨 아래: 카드마다 `LogoChoiceCard`. 가로로 스크롤되는 줄.
- '금액': 테두리 없는 큰 입력(`BasicTextField`, `BrandText.display`, 숫자 키패드). 아래에 2dp primary 밑줄, 오른쪽에 "원". 입력하는 동안 쉼표 표시(`VisualTransformation`).
- '가맹점': `OutlinedTextField`(6dp, hairline-input → focus 시 primary)
- '날짜와 시각': `PillChip`처럼 생긴 칩 두 개("📅" 대신 `Icons.Outlined.DateRange`)
  - 날짜 칩 → `DatePickerDialog` + `DatePicker`. 선택값은 시스템 시간대 기준 `LocalDate`로 바꾼다.
  - 시각 칩 → `TimePicker`를 담은 `AlertDialog`(24시간제).
- '할부' 줄: 오른쪽에 `Switch`
- 취소일 때 '원래 결제' 목록: 기존 라디오를 `WhiteCard` 줄로 바꾼다. 선택된 줄은 primary 테두리.
- `duplicateWarning`은 `CreamBanner` 스타일(누를 동작 없음)로 보여준다.
- 하단 고정 `PillButton("저장")`. 화면 전체에 `imePadding()`.

- [ ] **Step 1:** 구현 → `./gradlew :app:testDebugUnitTest :app:assembleDebug` (기존 `ManualEntryViewModelTest` 통과 유지)
- [ ] **Step 2:** 에뮬레이터에서 날짜·시각 선택이 저장값에 반영되는지 확인(저장 후 내역의 시각)
- [ ] **Step 3:** Commit — `style: 결제 직접 추가 화면과 달력·시계 선택`

---

### Task 7: 카드 목록과 카드 등록

**Files:**
- Modify: `ui/cards/CardListScreen.kt`, `ui/cards/CardEditScreen.kt` (ViewModel은 그대로)

화면 구성:
- **목록:**
  - 맨 위: `BrandTopBar("카드")`. 탭 화면이라 뒤로 버튼이 없다.
  - 카드마다 `WhiteCard`: 로고, 별칭, "카드사 · 마스크", 미인정 항목 수("미인정 항목 3개" caption)
  - 하단: `PillButton("＋ 카드 추가")`. 목록 아래 패딩을 둔다.
  - 빈 상태 문구: "아직 등록한 카드가 없어요"
- **등록·수정:**
  - 맨 위: `BrandTopBar("카드 추가"|"카드 수정", onBack)`
  - 카드사: `LogoChoiceCard` 2개
  - 끝 4자리: `DigitBoxes`. 아래에 caption "알림에는 {마스크}처럼 색칠한 자리만 보여요". 마스크는 `maskOf`, 4자리가 다 채워지기 전에는 "□*□*".
  - 별칭: `OutlinedTextField`
  - 충돌 경고: ruby 글자 caption
  - 미인정 항목: `FlowRow` 안의 `PillChip`
  - 하단 고정: `PillButton("저장")`. 수정일 때는 그 위에 "카드 삭제"(ink-mute 글자 버튼). `imePadding()`

- [ ] **Step 1:** 구현 → 테스트·빌드(기존 `CardEditViewModelTest` 통과 유지)
- [ ] **Step 2:** Commit — `style: 카드 목록과 카드 등록 화면`

---

### Task 8: 알림함, 설정, 권한 안내

**Files:**
- Modify: `ui/unparsed/UnparsedScreen.kt`, `ui/settings/SettingsScreen.kt`, `ui/setup/SetupScreen.kt`

화면 구성:
- **알림함:**
  - 맨 위: `BrandTopBar("알림함")`
  - 설명 caption: "카드 결제처럼 보이지만 읽지 못한 알림이에요"
  - 항목마다 `WhiteCard`: 받은 시각·앱 caption, 제목(Regular), 본문(soft 상자)
  - 항목 버튼:
    - 첫 줄: `PillButton("결제로 등록")`, `PillButton("취소로 등록", Secondary)`
    - 둘째 줄: '무시'와 '삭제' 글자 버튼(ink-mute)
  - 빈 상태: "읽지 못한 알림이 없어요"
- **설정:**
  - 맨 위: `BrandTopBar("설정")`
  - 백업 상태: `NavySummaryCard`가 아니라 `WhiteCard`에 담는다. 담을 내용은 계정, 마지막 백업, 경고(ruby caption).
  - 버튼: 연결은 `PillButton`, 지금 백업은 `PillButton`, 복원과 연결 해제는 Secondary
  - '권한 안내' `WhiteCard`(누르면 `SetupRoute`)를 추가한다.
  - 대화상자 문구는 그대로 둔다. 버튼만 알약 스타일로 바꾼다.
- **권한 안내:**
  - 맨 위: `BrandTopBar("권한 안내", onBack)`
  - 상태 카드 2개(`WhiteCard`): 켜짐은 `StatusTag("켜짐", Accent)`, 꺼짐은 `StatusTag("꺼짐", Excluded)`. 꺼졌을 때 `PillButton`을 둔다.
  - 안내 문구: soft 상자 안에 둔다.

- [ ] **Step 1:** 구현 → 테스트·빌드
- [ ] **Step 2:** Commit — `style: 알림함, 설정, 권한 안내 화면`

---

### Task 9: 에뮬레이터 점검과 마무리

- [ ] **Step 1: 준비** — 설치한 뒤 아래 데이터를 넣는다.
  - 카드 2장 + 별칭이 긴 카드 1장(예: "아주아주긴별칭카드이름입니다")
  - 결제: 긴 가맹점명, 취소, 할부, 미인정 건이 섞이게 몇 건
  - 인식 실패 1건
- [ ] **Step 2: 시안과 비교** — 메인, 내역(시트 포함), 직접 추가(달력·시계 포함), 카드 등록, 알림함, 설정, 권한 안내 스크린샷을 시안과 비교하고, 다른 곳을 고친다.
- [ ] **Step 3: Review Focus 1~5 확인**
  - 말줄임
  - 목록 끝이 가려지지 않는지
  - 0원 달
  - 키보드
  - `adb shell settings put system font_scale 1.3` 후 메인·내역 확인. 확인한 뒤 `font_scale 1.0`으로 되돌린다.
- [ ] **Step 4: 최종 확인** — `./gradlew :app:testDebugUnitTest :app:assembleDebug` / Expected: BUILD SUCCESSFUL, 133 tests 이상
- [ ] **Step 5:** Commit — `style: 화면 점검 수정`
