# CardSpending 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 카드 결제 알림(카카오톡 알림톡, 카드사 앱 푸시)을 읽어서 롯데·하나카드의 카드별 월 결제 금액과 실적인정 금액을 보여주고, 데이터를 사용자의 구글 드라이브에 백업하는 개인용 Android 앱을 만든다.

**Architecture:** 알림 리스너가 받은 알림을 메시지 단위(`NotificationMessage`)로 펼치고, 순수 Kotlin 파서·규칙(`parsing/`, `rules/`)이 거래로 바꾼 뒤 Room에 저장한다. 미인정 판정과 월 합계는 저장하지 않고 화면에 보여줄 때마다 계산한다. 화면은 Compose이고, 의존성은 `AppContainer` 하나로 직접 연결한다(DI 라이브러리 없음). 백업은 OkHttp로 Drive REST API의 appDataFolder에 JSON 파일을 올린다.

**Tech Stack:** Kotlin 2.4.21(AGP 9.4.1 내장 Kotlin), Jetpack Compose(BOM 2026.09.00, Material3), Navigation Compose 2.10.2, Room 2.8.5 + KSP 2.3.14, WorkManager 2.12.0, play-services-auth 22.0.0, OkHttp 5.5.1, kotlinx-serialization 1.13.0, JUnit4 + Robolectric 4.18.1 + MockWebServer3.

**Spec:** `docs/superpowers/specs/2026-11-18-card-spending-design.md` (구현 전에 반드시 함께 읽는다)

## Global Constraints

- 패키지 `com.jstensix.cardspending`, minSdk 31, compileSdk/targetSdk 37, Java/JVM target 11.
- 버전은 Task 1의 version catalog 값을 그대로 쓴다. 다른 라이브러리를 추가하지 않는다(Task 17의 `kotlinx-coroutines-play-services` 1.11.0만 예외).
- 지원 카드사는 롯데카드(`LOTTE`), 하나카드(`HANA`)뿐이다.
- 카드번호는 끝 4자리만 저장한다. 알림 속 가려진 번호(마스크)는 끝 4자리의 1·3번째만 보이는 `d*d*` 형식이다. 예: 끝 4자리 `7512` → `7*1*`.
- 결제와 관계없는 알림(`ParseOutcome.Ignored`)은 어떤 테이블에도 쓰지 않는다.
- 금액은 `Long` 원 단위의 양수로 저장하고, 승인/취소는 `TxKind`로 구분한다.
- 월 = 승인 일시 기준 해당 월 1일 00:00 이상, 다음 달 1일 00:00 미만.
- 승인 일시는 분 단위 `LocalDateTime`이다. DB에는 `yyyy-MM-dd'T'HH:mm` 고정 형식 문자열로 저장한다(문자열 비교 = 시간 비교).
- 외부 네트워크는 `https://www.googleapis.com/`(Drive, userinfo)만 쓴다.
- 화면 문구는 모두 한국어다.
- Robolectric 테스트는 `app/src/test/resources/robolectric.properties`의 `sdk=35`로 돌린다.
- 테스트 실행: `./gradlew :app:testDebugUnitTest --tests "<FQCN>"`, 빌드: `./gradlew :app:assembleDebug` (Git Bash, 저장소 루트).
- 테스트 단계에서 주석으로 적은 시나리오와 값은 그대로 assert 문으로 옮긴다. 주석 속 숫자와 문구가 기대값이다.
- 커밋 메시지 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`를 붙인다.
- 위 버전 조합은 2026-11-18에 AGP 9.4.1 + Gradle 9.6.0 + JDK 21에서 Compose·Room(KSP)·serialization·Robolectric·MockWebServer3 스모크 빌드로 확인했다.

## Review Focus

1. **카카오톡이 같은 채널 알림을 이전 메시지와 함께 다시 올리는 경우:** 결제마다 정확히 한 건만 남고, 사용자가 지운 결제는 되살아나지 않아야 한다. → Task 9 `kakaoRepost_insertsEachOnce`, `softDeleted_doesNotReappear`
2. **알림 문구의 공백·줄바꿈 변형(CRLF, NBSP, 줄 끝 공백, 하나 본문의 줄바꿈/` / ` 구분자):** 같은 결과로 파싱돼야 한다. → Task 3 `crlfAndNbsp_parsesSame`, Task 4 `newlineSeparated_parsesSame`
3. **월·연도 경계:** 11/30 23:59 승인 알림을 12/1에 받으면 11월에, 12/31 승인 알림을 1/1에 받으면 전년도 12월에 잡혀야 한다. → Task 2 `monthEndNotifiedNextMonth_staysInMonth`, `decemberNotifiedInJanuary_usesPreviousYear`, Task 8 `monthRange_includesLastMinute_excludesNextMonth`
4. **원래 결제 없이 들어온 취소(설치 전 결제의 취소):** 연결 없이 저장되고, 그 달 금액이 음수여도 그대로 보여야 한다. → Task 6 `cancelWithoutOriginal_usesOwnMerchantRules`, Task 7 `cancelOfPreviousMonth_reducesThisMonth`, Task 11 `formatWon_negative`
5. **백그라운드 백업 중 구글 권한 만료나 네트워크 오류:** 앱이 죽지 않고, 드라이브의 기존 백업을 지우지 않고, 메인 화면에 경고가 떠야 한다. → Task 17 `needsReconnect_doesNotUpload_setsWarning`, `uploadFailure_keepsExistingBackups`

## File Structure

```
app/src/main/java/com/jstensix/cardspending/
  CardSpendingApp.kt            Application, AppContainer 보관, 시작 시 재파싱
  AppContainer.kt               DB·저장소·인제스터·백업 객체 생성과 연결
  MainActivity.kt               Compose 진입점
  model/Enums.kt                Issuer, TxKind, TxSource
  model/Card.kt                 @Entity 카드
  model/Transaction.kt          @Entity 거래
  model/UnparsedNotification.kt @Entity 인식 실패 알림
  model/ExclusionItem.kt        고정 미인정 항목과 매칭
  model/LocalDateTimeSerializer.kt  백업 JSON용 직렬화기
  parsing/NotificationMessage.kt    파서 입력 단위
  parsing/ParsedTransaction.kt      파서 출력
  parsing/ParseText.kt          금액·줄 정규화·연도 추론
  parsing/LotteParser.kt
  parsing/HanaParser.kt
  parsing/CardNotificationParser.kt 판별·후보 감지·PARSER_VERSION
  rules/CardMask.kt             마스크 계산, 카드-거래 매칭, 번호 충돌
  rules/Recognition.kt          인정/미인정 판정
  rules/Dedup.kt                중복 판정
  rules/CancelLinker.kt         취소의 원래 결제 찾기
  rules/MonthlySummary.kt       월 범위와 카드별 합계
  data/Converters.kt, CardDao.kt, TransactionDao.kt, UnparsedDao.kt, AppDatabase.kt
  data/SpendingRepository.kt    모든 DB 읽기/쓰기, 쓰기 후 onChanged 호출
  ingest/Ingestor.kt            알림 메시지 → 저장, 재파싱
  notification/NotificationExtractor.kt  Notification → NotificationMessage 목록
  notification/CardNotificationListener.kt
  notification/DeviceStatus.kt  알림 접근·배터리 최적화 상태
  backup/BackupData.kt          백업 JSON 형식과 코덱
  backup/DriveApi.kt, DriveClient.kt     Drive REST
  backup/BackupPrefs.kt         연결 계정·마지막 백업·오류
  backup/BackupManager.kt       백업·복원·보관 개수 관리
  backup/DriveAuth.kt           구글 권한(AuthorizationClient)
  backup/BackupWorker.kt, BackupScheduler.kt
  ui/theme/Theme.kt, ui/Format.kt, ui/IssuerLogo.kt, ui/AppNavHost.kt
  ui/main/, ui/cards/, ui/detail/, ui/entry/, ui/unparsed/, ui/setup/, ui/settings/
app/src/main/res/drawable-nodpi/logo_lotte.png, logo_hana.png
docs/google-cloud-setup.md
```

---

### Task 1: 프로젝트 설정 (git, 의존성, Compose 진입점)

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/themes.xml`
- Delete: `app/src/main/res/values-night/themes.xml`
- Create: `app/src/main/java/com/jstensix/cardspending/MainActivity.kt`, `app/src/test/resources/robolectric.properties`, `app/src/test/java/com/jstensix/cardspending/RobolectricSmokeTest.kt`

**Interfaces:**
- Produces: 빌드 가능한 Compose 앱, 아래 version catalog 별칭(`libs.*`)

- [ ] **Step 1: git 저장소를 만들고 현재 템플릿을 커밋**

```bash
git init
git add -A
git commit -m "chore: Android 템플릿과 설계 문서"
```

- [ ] **Step 2: version catalog 교체**

`gradle/libs.versions.toml`의 `[versions]`/`[libraries]`/`[plugins]`를 아래 값으로 바꾼다. appcompat, material, espresso는 지운다.

```toml
[versions]
agp = "9.4.1"
kotlin = "2.4.21"
ksp = "2.3.14"
coreKtx = "1.19.1"
activityCompose = "1.13.0"
composeBom = "2026.09.00"
lifecycle = "2.11.0"
navigationCompose = "2.10.2"
room = "2.8.5"
work = "2.12.0"
playServicesAuth = "22.0.0"
okhttp = "5.5.1"
coroutines = "1.11.0"
serializationJson = "1.13.0"
junit = "4.13.2"
robolectric = "4.18.1"
androidxTestCore = "1.7.0"
androidxJunit = "1.3.0"
```

라이브러리 별칭: `androidx-core-ktx`, `androidx-activity-compose`, `androidx-compose-bom`, `androidx-compose-ui`, `androidx-compose-ui-tooling-preview`, `androidx-compose-ui-tooling`, `androidx-compose-material3`, `androidx-lifecycle-viewmodel-compose`, `androidx-lifecycle-runtime-compose`, `androidx-navigation-compose`, `androidx-room-runtime`, `androidx-room-ktx`, `androidx-room-compiler`, `androidx-work-runtime-ktx`, `play-services-auth`, `okhttp`, `okhttp-mockwebserver3`(`com.squareup.okhttp3:mockwebserver3`), `kotlinx-coroutines-android`, `kotlinx-coroutines-test`, `kotlinx-serialization-json`, `junit`, `robolectric`, `androidx-test-core`, `androidx-junit`.
플러그인 별칭: `android-application`, `kotlin-compose`(`org.jetbrains.kotlin.plugin.compose`), `kotlin-serialization`(`org.jetbrains.kotlin.plugin.serialization`), `ksp`(`com.google.devtools.ksp`).

- [ ] **Step 3: Gradle 스크립트 수정**

루트 `build.gradle.kts`: 네 플러그인 모두 `apply false`.
`app/build.gradle.kts`: 플러그인 `android.application`, `kotlin.compose`, `kotlin.serialization`, `ksp` 적용. `buildFeatures { compose = true }`, `testOptions { unitTests.isIncludeAndroidResources = true }`, `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`. 의존성은 위 라이브러리 전부(Compose는 `platform(bom)`, `room-compiler`는 `ksp`, `ui-tooling`은 `debugImplementation`, 테스트 5개는 `testImplementation`).

- [ ] **Step 4: 테마와 매니페스트**

`values/themes.xml`의 `Theme.CardSpending` 부모를 `android:Theme.Material.Light.NoActionBar`로 바꾸고 안의 item은 지운다. `values-night/themes.xml`은 삭제. 매니페스트에 `.MainActivity`(exported, MAIN/LAUNCHER)를 추가한다. `MainActivity`는 `ComponentActivity`이고 `setContent { MaterialTheme { Text("CardSpending") } }`만 한다.

- [ ] **Step 5: Robolectric 스모크 테스트 작성**

`robolectric.properties`: `sdk=35`

```kotlin
@RunWith(RobolectricTestRunner::class)
class RobolectricSmokeTest {
    @Test fun appContext_hasPackageName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.jstensix.cardspending", context.packageName)
    }
}
```

- [ ] **Step 6: 빌드와 테스트 확인**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, `RobolectricSmokeTest`와 `ExampleUnitTest` 통과

- [ ] **Step 7: Commit** — `git add -A && git commit -m "build: Compose, Room, WorkManager, OkHttp 설정"`

---

### Task 2: 파싱 기본 요소

**Files:**
- Create: `model/Enums.kt`, `parsing/NotificationMessage.kt`, `parsing/ParsedTransaction.kt`, `parsing/ParseText.kt`, `rules/CardMask.kt`
- Test: `parsing/ParseTextTest.kt`, `rules/CardMaskTest.kt` (모두 `app/src/test/java/com/jstensix/cardspending/` 아래, 이하 동일)

**Interfaces:**
- Produces:
```kotlin
enum class Issuer(val displayName: String) { LOTTE("롯데카드"), HANA("하나카드") }
enum class TxKind { APPROVAL, CANCEL }
enum class TxSource { NOTIFICATION, MANUAL }

data class NotificationMessage(val packageName: String, val title: String?, val body: String, val postedAt: LocalDateTime)

data class ParsedTransaction(
    val issuer: Issuer, val kind: TxKind, val amount: Long, val merchant: String,
    val mask: String, val installment: Boolean, val approvedAt: LocalDateTime,
    val cumulative: Long?, val rawText: String,
)

fun parseWon(text: String): Long?
fun normalizeLines(text: String): List<String>
fun inferApprovedAt(month: Int, day: Int, hour: Int, minute: Int, postedAt: LocalDateTime): LocalDateTime?
fun maskOf(last4: String): String
fun isValidLast4(value: String): Boolean
```

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
class ParseTextTest {
    @Test fun parseWon_handlesCommas() {
        assertEquals(11000L, parseWon("11,000"))
        assertEquals(3220782L, parseWon("3,220,782"))
        assertEquals(900L, parseWon("900"))
        assertNull(parseWon("abc"))
    }
    @Test fun normalizeLines_trimsAndDropsBlanks() {
        assertEquals(listOf("a", "b", "c"), normalizeLines("a\r\n b \n\n c"))
    }
    @Test fun sameMonth_usesPostedYear() {
        assertEquals(LocalDateTime.of(2026, 11, 18, 11, 43),
            inferApprovedAt(11, 18, 11, 43, LocalDateTime.of(2026, 11, 18, 11, 43, 5)))
    }
    @Test fun monthEndNotifiedNextMonth_staysInMonth() {
        assertEquals(LocalDateTime.of(2026, 11, 30, 23, 59),
            inferApprovedAt(11, 30, 23, 59, LocalDateTime.of(2026, 12, 1, 0, 0, 30)))
    }
    @Test fun decemberNotifiedInJanuary_usesPreviousYear() {
        assertEquals(LocalDateTime.of(2026, 12, 31, 23, 59),
            inferApprovedAt(12, 31, 23, 59, LocalDateTime.of(2027, 1, 1, 0, 0, 10)))
    }
    @Test fun invalidDate_returnsNull() {
        assertNull(inferApprovedAt(2, 30, 10, 0, LocalDateTime.of(2026, 3, 1, 9, 0)))
    }
}

class CardMaskTest {
    @Test fun maskOf_keepsFirstAndThirdDigits() { assertEquals("7*1*", maskOf("7512")) }
    @Test fun isValidLast4_requiresFourDigits() {
        assertTrue(isValidLast4("0381")); assertFalse(isValidLast4("75a2")); assertFalse(isValidLast4("123"))
    }
}
```

- [ ] **Step 2: 실패 확인** — Run: `./gradlew :app:testDebugUnitTest --tests "*ParseTextTest" --tests "*CardMaskTest"` / Expected: 컴파일 실패(미정의)

- [ ] **Step 3: 구현**

`normalizeLines`: `\r\n`·`\r` → `\n`, ` `·`​` → 공백, 줄마다 trim, 빈 줄 제거. `inferApprovedAt`: 연도는 `postedAt.year`, 단 `month > postedAt.monthValue`이면 `year - 1`. 날짜가 없으면(`DateTimeException`) null.

- [ ] **Step 4: 통과 확인** — 같은 명령 / Expected: PASS

- [ ] **Step 5: Commit** — `feat: 파싱 기본 요소와 카드번호 마스크`

---

### Task 3: 롯데카드 파서

**Files:**
- Create: `parsing/LotteParser.kt`
- Test: `parsing/LotteParserTest.kt`, `parsing/Samples.kt`(테스트 픽스처, Task 4·5도 사용)

**Interfaces:**
- Consumes: Task 2 전부
- Produces: `object LotteParser { fun parse(message: NotificationMessage): ParsedTransaction? }`

형식(스펙 5.3): 금액 줄 `^([\d,]+)원\s*(.*)$`이고 뒷부분에 `승인` 또는 `취소`가 있어야 한다. `취소`가 있으면 `CANCEL`. 가맹점 = 금액 줄 바로 앞 줄. 마스크 = 금액 줄 다음 줄 끝의 `\((\d\*\d\*)\)`. 그다음 줄 `^(.+?),\s*(\d{1,2})/(\d{1,2})\s+(\d{1,2}):(\d{2})$`, 앞부분이 `일시불`이 아니면 할부. 누적 = 아무 줄의 `누적금액\s*([\d,]+)원`(없어도 됨). `rawText` = 원래 body.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
// Samples.kt
internal const val LOTTE_SAMPLE = "세븐일레븐 판교LH테크노점\n11,000원 승인\n김수 텔로 SE(7*1*)\n일시불, 11/18 11:43\n누적금액 3,220,782원\n\n채널 추가하고 이 채널의 마케팅 메시지 등을 카카오톡으로 받기"

// LotteParserTest.kt
private val POSTED = LocalDateTime.of(2026, 11, 18, 11, 43, 20)
private const val SAMPLE = LOTTE_SAMPLE
private fun msg(body: String) = NotificationMessage("com.kakao.talk", "롯데카드", body, POSTED)

class LotteParserTest {
    @Test fun sample_parses() {
        assertEquals(
            ParsedTransaction(Issuer.LOTTE, TxKind.APPROVAL, 11000, "세븐일레븐 판교LH테크노점", "7*1*", false,
                LocalDateTime.of(2026, 11, 18, 11, 43), 3220782, SAMPLE),
            LotteParser.parse(msg(SAMPLE)))
    }
    @Test fun crlfAndNbsp_parsesSame() {
        val variant = SAMPLE.replace("\n", "\r\n").replace("11,000원 승인", "11,000원 승인 ")
        assertEquals(11000L, LotteParser.parse(msg(variant))?.amount)
        assertEquals("7*1*", LotteParser.parse(msg(variant))?.mask)
    }
    @Test fun installmentLine_setsInstallment() {
        assertEquals(true, LotteParser.parse(msg(SAMPLE.replace("일시불,", "3개월,")))?.installment)
    }
    @Test fun cancelWords_setCancel() {
        assertEquals(TxKind.CANCEL, LotteParser.parse(msg(SAMPLE.replace("원 승인", "원 승인취소")))?.kind)
        assertEquals(TxKind.CANCEL, LotteParser.parse(msg(SAMPLE.replace("원 승인", "원 취소")))?.kind)
    }
    @Test fun missingCumulative_stillParses() {
        assertNull(LotteParser.parse(msg(SAMPLE.replace("누적금액 3,220,782원", "")))?.cumulative)
        assertNotNull(LotteParser.parse(msg(SAMPLE.replace("누적금액 3,220,782원", ""))))
    }
    @Test fun missingDateLine_returnsNull() {
        assertNull(LotteParser.parse(msg(SAMPLE.replace("일시불, 11/18 11:43\n", ""))))
    }
    @Test fun unrelatedText_returnsNull() { assertNull(LotteParser.parse(msg("안녕하세요"))) }
}
```

- [ ] **Step 2: 실패 확인** — `--tests "*LotteParserTest"` / Expected: 컴파일 실패
- [ ] **Step 3: 구현** (`normalizeLines`, `parseWon`, `inferApprovedAt` 사용)
- [ ] **Step 4: 통과 확인** — Expected: PASS
- [ ] **Step 5: Commit** — `feat: 롯데카드 알림 파서`

---

### Task 4: 하나카드 파서 (앱 푸시)

**Files:**
- Create: `parsing/HanaParser.kt`
- Test: `parsing/HanaParserTest.kt`

**Interfaces:**
- Produces: `object HanaParser { fun parse(message: NotificationMessage): ParsedTransaction? }`

형식(스펙 5.3): 제목 `^\((.+?)\)\s*([\d,]+)원$`, 괄호 안에 `취소`가 있으면 `CANCEL`, `결제`·`승인`이면 `APPROVAL`, 그 외 null. 본문은 `normalizeLines` 후 각 줄을 ` / `로 다시 나눠 조각 목록을 만든다. 조각 0 = 가맹점, 조각 1 = `^(신용|체크)\((.+?),(\d\*\d\*)\)$`(가운데가 `일시불`이 아니면 할부), 조각 2 = `^(\d{1,2})\.(\d{1,2})\s+(\d{1,2}):(\d{2})$`, 누적 = `누적이용금액\s*([\d,]+)원`(없어도 됨). `rawText` = `"$title\n$body"`.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
// Samples.kt에 추가
internal const val HANA_TITLE = "(결제) 9,900원"
internal const val HANA_BODY = "거궁찬판교점 / 신용(일시불,0*8*) / 11.18 17:29 / 누적이용금액 532,231원"

// HanaParserTest.kt
private val POSTED = LocalDateTime.of(2026, 11, 18, 17, 29, 40)
private const val BODY = HANA_BODY
private fun msg(title: String?, body: String = BODY) = NotificationMessage("com.hanacard.app", title, body, POSTED)

class HanaParserTest {
    @Test fun sample_parses() {
        val tx = HanaParser.parse(msg("(결제) 9,900원"))!!
        assertEquals(Issuer.HANA, tx.issuer); assertEquals(TxKind.APPROVAL, tx.kind)
        assertEquals(9900L, tx.amount); assertEquals("거궁찬판교점", tx.merchant)
        assertEquals("0*8*", tx.mask); assertFalse(tx.installment)
        assertEquals(LocalDateTime.of(2026, 11, 18, 17, 29), tx.approvedAt)
        assertEquals(532231L, tx.cumulative)
    }
    @Test fun newlineSeparated_parsesSame() {
        val a = HanaParser.parse(msg("(결제) 9,900원"))!!
        val b = HanaParser.parse(msg("(결제) 9,900원", BODY.replace(" / ", "\n")))!!
        assertEquals(a.copy(rawText = ""), b.copy(rawText = ""))
    }
    @Test fun cancelTitles_setCancel() {
        assertEquals(TxKind.CANCEL, HanaParser.parse(msg("(취소) 9,900원"))?.kind)
        assertEquals(TxKind.CANCEL, HanaParser.parse(msg("(결제취소) 9,900원"))?.kind)
    }
    @Test fun checkCardInstallment_setsInstallment() {
        assertEquals(true, HanaParser.parse(msg("(결제) 9,900원", BODY.replace("신용(일시불,", "체크(3개월,")))?.installment)
    }
    @Test fun missingTitle_returnsNull() { assertNull(HanaParser.parse(msg(null))) }
    @Test fun unrelatedBody_returnsNull() { assertNull(HanaParser.parse(msg("(결제) 9,900원", "이벤트 안내"))) }
}
```

- [ ] **Step 2: 실패 확인** — `--tests "*HanaParserTest"`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: Commit** — `feat: 하나카드 앱 푸시 파서`

---

### Task 5: 알림 판별기 (파싱 / 인식 실패 후보 / 무시)

**Files:**
- Create: `parsing/CardNotificationParser.kt`
- Test: `parsing/CardNotificationParserTest.kt`

**Interfaces:**
- Produces:
```kotlin
const val PARSER_VERSION = 1   // 파서를 바꿀 때마다 1씩 올린다(재파싱 트리거)
sealed interface ParseOutcome {
    data class Parsed(val tx: ParsedTransaction) : ParseOutcome
    data object Candidate : ParseOutcome
    data object Ignored : ParseOutcome
}
object CardNotificationParser { fun parse(message: NotificationMessage, knownPackages: Set<String>): ParseOutcome }
```

순서: `LotteParser` → `HanaParser` → 후보 판정. 후보 = (a) 제목이나 본문에 `롯데카드`/`하나카드`가 있음, 또는 (b) 본문에 `\d\*\d\*`와 `[\d,]+원`이 모두 있음, 또는 (c) `packageName in knownPackages`이고 제목+본문에 `[\d,]+원`과 `승인|결제|취소` 중 하나가 있음. 나머지는 `Ignored`.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
class CardNotificationParserTest {
    private val t = LocalDateTime.of(2026, 11, 18, 12, 0)
    private fun m(pkg: String, title: String?, body: String) = NotificationMessage(pkg, title, body, t)

    @Test fun lotteSample_parsed() { /* m("com.kakao.talk", "롯데카드", LOTTE_SAMPLE) → Parsed, tx.issuer == LOTTE */ }
    @Test fun hanaSample_parsed() { /* m("com.hanacard.app", HANA_TITLE, HANA_BODY) → Parsed, tx.issuer == HANA */ }
    @Test fun privateChat_ignored() {
        assertEquals(ParseOutcome.Ignored, CardNotificationParser.parse(m("com.kakao.talk", "엄마", "저녁 먹었어?"), emptySet()))
    }
    @Test fun unknownIssuerFormat_candidate() {
        assertEquals(ParseOutcome.Candidate, CardNotificationParser.parse(m("com.kakao.talk", "하나카드", "[하나카드] 홍*동님 9,900원 승인"), emptySet()))
    }
    @Test fun maskAndAmountInUnknownLayout_candidate() {
        assertEquals(ParseOutcome.Candidate, CardNotificationParser.parse(m("x", null, "카드(0*8*) 12,000원"), emptySet()))
    }
    @Test fun knownPackagePaymentText_candidate_unknownPackage_ignored() {
        val body = "결제 12,000원 완료"
        assertEquals(ParseOutcome.Candidate, CardNotificationParser.parse(m("com.hana.app", "알림", body), setOf("com.hana.app")))
        assertEquals(ParseOutcome.Ignored, CardNotificationParser.parse(m("com.shop", "쿠팡", body), setOf("com.hana.app")))
    }
}
```

- [ ] **Step 2: 실패 확인** — `--tests "*CardNotificationParserTest"`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — `--tests "com.jstensix.cardspending.parsing.*"` 전부 PASS
- [ ] **Step 5: Commit** — `feat: 카드 알림 판별과 인식 실패 후보 감지`

---

### Task 6: 엔티티, 미인정 항목, 인정 판정

**Files:**
- Create: `model/Card.kt`, `model/Transaction.kt`, `model/UnparsedNotification.kt`, `model/ExclusionItem.kt`, `model/LocalDateTimeSerializer.kt`, `rules/Recognition.kt`
- Test: `model/ExclusionItemTest.kt`, `rules/RecognitionTest.kt`

**Interfaces:**
- Produces (세 엔티티 모두 `@Entity` + `@Serializable`, `LocalDateTime` 필드는 `@Serializable(with = LocalDateTimeSerializer::class)`, 백업 호환을 위해 새 필드는 항상 기본값을 둔다):
```kotlin
@Entity(tableName = "cards")
data class Card(@PrimaryKey(autoGenerate = true) val id: Long = 0, val issuer: Issuer, val last4: String,
                val nickname: String, val exclusions: Set<ExclusionItem> = emptySet())

@Entity(tableName = "transactions", indices = [Index("issuer", "mask", "approvedAt")])
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val issuer: Issuer, val kind: TxKind, val amount: Long, val merchant: String, val mask: String,
    val installment: Boolean, val approvedAt: LocalDateTime, val cumulative: Long? = null,
    val source: TxSource, val sourcePackage: String? = null, val rawText: String? = null,
    val manualRecognition: Boolean? = null,   // null = 규칙대로
    val originalId: Long? = null,             // 취소 → 원래 결제
    val deleted: Boolean = false,             // 알림 거래 삭제는 소프트 삭제(중복 판정용 흔적)
    val createdAt: LocalDateTime,
)

@Entity(tableName = "unparsed", indices = [Index(value = ["contentHash"], unique = true)])
data class UnparsedNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val contentHash: String,
    val packageName: String, val title: String?, val body: String, val postedAt: LocalDateTime,
    val parserVersion: Int, val dismissed: Boolean = false,
)

enum class ExclusionItem(val label: String, val keywords: List<String>, val byInstallment: Boolean = false) {
    TAX("세금", listOf("국세", "지방세", "위택스", "홈택스", "세무서")),
    UTILITY("공과금", listOf("한국전력", "한전", "도시가스", "상수도", "수도요금")),
    APARTMENT_FEE("아파트관리비", listOf("관리비", "관리사무소")),
    GIFT_PREPAID("상품권·선불충전", listOf("상품권", "기프트카드", "선불")),
    INSURANCE("보험료", listOf("보험", "생명", "화재", "손해")),
    TUITION("등록금", listOf("등록금", "대학교")),
    INSTALLMENT("할부 결제", emptyList(), byInstallment = true);
    fun matches(merchant: String, installment: Boolean): Boolean
}

sealed interface RecognitionReason {
    data object Rule : RecognitionReason
    data class Excluded(val item: ExclusionItem) : RecognitionReason
    data object Manual : RecognitionReason
}
data class Recognition(val recognized: Boolean, val reason: RecognitionReason)
fun recognize(tx: Transaction, card: Card, original: Transaction?): Recognition
```

판정 순서: `tx.manualRecognition`이 있으면 Manual → 취소이고 `original`이 있으면 `recognize(original, card, null)` → `card.exclusions` 중 enum 순서상 처음 매칭되는 항목이 있으면 Excluded → 아니면 Rule(인정).

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
class ExclusionItemTest {
    @Test fun keywordItems_matchMerchant() {
        assertTrue(ExclusionItem.TAX.matches("국세(카드납부)", false))
        assertTrue(ExclusionItem.UTILITY.matches("한국전력공사", false))
        assertFalse(ExclusionItem.INSURANCE.matches("세븐일레븐", false))
    }
    @Test fun installmentItem_matchesOnlyInstallment() {
        assertTrue(ExclusionItem.INSTALLMENT.matches("아무가게", true))
        assertFalse(ExclusionItem.INSTALLMENT.matches("아무가게", false))
    }
}

class RecognitionTest {
    private val at = LocalDateTime.of(2026, 11, 18, 12, 0)
    private fun tx(merchant: String, kind: TxKind = TxKind.APPROVAL, manual: Boolean? = null) =
        Transaction(issuer = Issuer.LOTTE, kind = kind, amount = 1000, merchant = merchant, mask = "7*1*",
            installment = false, approvedAt = at, source = TxSource.NOTIFICATION, manualRecognition = manual, createdAt = at)
    private val taxCard = Card(1, Issuer.LOTTE, "7512", "롯데", setOf(ExclusionItem.TAX))

    @Test fun excludedMerchant_notRecognized() {
        assertEquals(Recognition(false, RecognitionReason.Excluded(ExclusionItem.TAX)), recognize(tx("지방세 납부"), taxCard, null))
    }
    @Test fun noExclusions_recognized() {
        assertEquals(Recognition(true, RecognitionReason.Rule), recognize(tx("지방세 납부"), taxCard.copy(exclusions = emptySet()), null))
    }
    @Test fun manualOverridesRule() {
        assertEquals(Recognition(true, RecognitionReason.Manual), recognize(tx("지방세 납부", manual = true), taxCard, null))
    }
    @Test fun cancel_followsOriginal() {
        assertEquals(Recognition(false, RecognitionReason.Excluded(ExclusionItem.TAX)),
            recognize(tx("다른이름", TxKind.CANCEL), taxCard, tx("국세")))
    }
    @Test fun cancelManual_overridesOriginal() {
        assertEquals(Recognition(false, RecognitionReason.Manual), recognize(tx("x", TxKind.CANCEL, manual = false), taxCard, tx("편의점")))
    }
    @Test fun cancelWithoutOriginal_usesOwnMerchantRules() {
        assertEquals(Recognition(false, RecognitionReason.Excluded(ExclusionItem.TAX)), recognize(tx("국세", TxKind.CANCEL), taxCard, null))
    }
}
```

- [ ] **Step 2: 실패 확인** — `--tests "*ExclusionItemTest" --tests "*RecognitionTest"`
- [ ] **Step 3: 구현** (`LocalDateTimeSerializer`는 `yyyy-MM-dd'T'HH:mm` 문자열)
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: Commit** — `feat: 엔티티, 미인정 항목, 인정 판정`

---

### Task 7: 중복 판정, 취소 연결, 월 합계

**Files:**
- Create: `rules/Dedup.kt`, `rules/CancelLinker.kt`, `rules/MonthlySummary.kt`
- Modify: `rules/CardMask.kt`
- Test: `rules/DedupTest.kt`, `rules/CancelLinkerTest.kt`, `rules/MonthlySummaryTest.kt`, `rules/CardMaskTest.kt`

**Interfaces:**
- Produces:
```kotlin
// CardMask.kt 추가
fun Card.owns(tx: Transaction): Boolean            // issuer 같고 maskOf(last4) == tx.mask
fun findMaskConflict(cards: List<Card>, candidate: Card): Card?   // 같은 issuer·같은 마스크·다른 id

fun isSameTransaction(existing: Transaction, incoming: ParsedTransaction): Boolean
fun findOriginal(cancel: ParsedTransaction, approvals: List<Transaction>): Transaction?

data class CardMonthSummary(val card: Card, val payment: Long, val recognized: Long)
data class MonthSummary(val cards: List<CardMonthSummary>, val unregisteredCount: Int)
fun monthBounds(month: YearMonth): Pair<LocalDateTime, LocalDateTime>   // [1일 00:00, 다음달 1일 00:00)
fun summarizeMonth(cards: List<Card>, transactions: List<Transaction>, originalsById: Map<Long, Transaction>): MonthSummary
```

- `isSameTransaction`: issuer, mask, amount, kind, approvedAt이 모두 같고, 누적금액이 같거나 한쪽이 null.
- `findOriginal`: 후보 = `kind == APPROVAL`, `!deleted`, `amount >= cancel.amount`, `approvedAt <= cancel.approvedAt`. 가맹점이 같은 후보가 있으면 그중에서, 없으면 전체에서 `approvedAt`이 가장 늦은 것.
- `summarizeMonth`: `deleted` 제외. 카드마다 `owns`인 거래로 payment = 승인 합 − 취소 합, recognized = 인정된 승인 합 − 인정된 취소 합(`recognize(tx, card, originalsById[tx.originalId])`). 어떤 카드도 `owns`하지 않는 거래 수 = `unregisteredCount`. 거래가 없는 카드도 0/0으로 포함. 카드 순서 = 입력 순서.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
// 공통 픽스처: at(d, h, m) = LocalDateTime.of(2026, 11, d, h, m); tx(...) / parsed(...) 빌더는 테스트 파일 안에 둔다.
class DedupTest {
    @Test fun identical_isSame()                    // cumulative 532231 양쪽 → true
    @Test fun differentCumulative_isDifferent()     // 같은 분·같은 금액, 누적 532231 vs 542131 → false
    @Test fun nullCumulative_isSame()               // existing.cumulative == null → true
    @Test fun differentMinuteOrKind_isDifferent()   // 분 다름 → false, kind 다름 → false
}
class CancelLinkerTest {
    @Test fun picksLatestEligibleApproval()         // 11/10·11/15 승인(각 11,000) + 11/16 취소 11,000 → 11/15
    @Test fun prefersSameMerchant()                 // 11/15 "A" vs 11/16 "B", 취소 가맹점 "A" → 11/15 "A"
    @Test fun ignoresLaterApprovals_returnsNullWhenNone()  // 취소보다 늦은 승인만 → null
    @Test fun partialCancel_links()                 // 승인 11,000, 취소 5,000 → 연결
}
class MonthlySummaryTest {
    @Test fun excludedAndCancel_computeTotals() {
        // 카드 {TAX}: 11월 승인 10,000 "편의점", 50,000 "국세", 취소 3,000 (originalId=편의점)
        // → payment 57,000, recognized 7,000
    }
    @Test fun cancelOfPreviousMonth_reducesThisMonth() {
        // 11월 거래 목록 = [취소 20,000 (originalId → 10월 승인 20,000 "편의점")], originals에 10월 승인 포함
        // → payment -20,000, recognized -20,000
    }
    @Test fun unmatchedMask_countsUnregistered() // 마스크 "9*9*" 거래 → unregisteredCount 1, 카드 합계 0
    @Test fun deleted_ignored()                 // deleted=true 승인 → 합계 0
    @Test fun cardWithoutTransactions_hasZeroRow()
    @Test fun monthBounds_novemberRange() {
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0) to LocalDateTime.of(2026, 12, 1, 0, 0), monthBounds(YearMonth.of(2026, 11)))
    }
}
// CardMaskTest 추가
@Test fun findMaskConflict_sameIssuerSameMask()   // LOTTE 7512 기존, LOTTE 7913 신규 → 기존 카드 반환
@Test fun findMaskConflict_otherIssuer_null()     // HANA 7512 신규 → null
```
각 테스트 주석의 값이 단언의 기대값이다(구현자가 assert 문으로 옮긴다).

- [ ] **Step 2: 실패 확인** — `--tests "com.jstensix.cardspending.rules.*"`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: Commit** — `feat: 중복 판정, 취소 연결, 월 합계`

---

### Task 8: Room DB와 저장소

**Files:**
- Create: `data/Converters.kt`, `data/CardDao.kt`, `data/TransactionDao.kt`, `data/UnparsedDao.kt`, `data/AppDatabase.kt`, `data/SpendingRepository.kt`, `app/schemas/`(KSP가 생성, 커밋)
- Test: `data/SpendingRepositoryTest.kt` (Robolectric, `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries()`)

**Interfaces:**
- Consumes: Task 6 엔티티, Task 7 `monthBounds`
- Produces:
```kotlin
@Database(entities = [Card::class, Transaction::class, UnparsedNotification::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() { /* cardDao(), transactionDao(), unparsedDao() */
    companion object { fun create(context: Context): AppDatabase }   // 파일명 "cardspending.db"
}

data class MonthData(val transactions: List<Transaction>, val originalsById: Map<Long, Transaction>)

class SpendingRepository(private val db: AppDatabase, private val onChanged: () -> Unit) {
    fun observeCards(): Flow<List<Card>>
    suspend fun getCard(id: Long): Card?
    suspend fun saveCard(card: Card): Long
    suspend fun deleteCard(id: Long)                       // 거래는 남긴다
    fun observeMonth(month: YearMonth): Flow<MonthData>    // deleted 제외, 범위 안 + 취소의 원래 결제
    suspend fun getTransaction(id: Long): Transaction?
    suspend fun addManual(tx: Transaction): Long
    suspend fun setManualRecognition(txId: Long, value: Boolean?)
    suspend fun deleteTransaction(txId: Long)              // NOTIFICATION → deleted=true, MANUAL → 행 삭제
    suspend fun approvalsForCancel(issuer: Issuer, mask: String, before: LocalDateTime, minAmount: Long): List<Transaction>
    suspend fun findSameKey(issuer: Issuer, mask: String, amount: Long, kind: TxKind, approvedAt: LocalDateTime): List<Transaction> // NOTIFICATION만, deleted 포함
    suspend fun insertNotificationTx(tx: Transaction): Long
    suspend fun knownSourcePackages(): Set<String>
    fun observeUnparsed(): Flow<List<UnparsedNotification>>  // dismissed 제외, postedAt 내림차순
    suspend fun getUnparsed(id: Long): UnparsedNotification?
    suspend fun insertUnparsedIfNew(item: UnparsedNotification)   // contentHash 충돌 시 무시
    suspend fun pendingUnparsed(currentVersion: Int): List<UnparsedNotification>  // !dismissed && parserVersion < currentVersion
    suspend fun updateUnparsedVersion(id: Long, version: Int)
    suspend fun dismissUnparsed(id: Long)
    suspend fun isEmpty(): Boolean                          // 카드·거래 모두 0건
    suspend fun snapshot(): Triple<List<Card>, List<Transaction>, List<UnparsedNotification>>
    suspend fun replaceAll(cards: List<Card>, transactions: List<Transaction>, unparsed: List<UnparsedNotification>)  // 한 트랜잭션, id 유지
}
```
Converters: enum ↔ `name`, `LocalDateTime` ↔ `yyyy-MM-dd'T'HH:mm`, `Set<ExclusionItem>` ↔ 쉼표로 이은 `name`(모르는 이름은 버림). 모든 쓰기 메서드는 성공 후 `onChanged()`를 부른다(`replaceAll` 제외).

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
@RunWith(RobolectricTestRunner::class)
class SpendingRepositoryTest {
    // setUp: in-memory DB, changes = 0, repo = SpendingRepository(db) { changes++ }
    @Test fun saveCard_roundTripsExclusions()            // exclusions {TAX, INSTALLMENT} 그대로 읽힘
    @Test fun monthRange_includesLastMinute_excludesNextMonth()  // 11/30 23:59 포함, 12/01 00:00 제외 (observeMonth(2026-11).first())
    @Test fun observeMonth_includesOriginalOfCancel()    // 11월 취소의 originalId → 10월 승인이 originalsById에 있음
    @Test fun deleteNotificationTx_isSoft()              // 삭제 후 observeMonth에 없음, findSameKey에는 있음
    @Test fun deleteManualTx_isHard()                    // 삭제 후 getTransaction == null
    @Test fun insertUnparsedIfNew_ignoresSameHash()      // 같은 hash 두 번 → 1행
    @Test fun dismissedUnparsed_hiddenButBlocksReinsert()// dismiss 후 observeUnparsed 비어 있음, 같은 hash 재삽입해도 비어 있음
    @Test fun writes_callOnChanged()                     // saveCard, addManual, setManualRecognition 후 changes == 3
    @Test fun knownSourcePackages_distinct()             // 두 패키지 3건 → 크기 2
}
```

- [ ] **Step 2: 실패 확인** — `--tests "*SpendingRepositoryTest"`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: Commit** — `feat: Room DB와 저장소` (`app/schemas/` 포함)

---

### Task 9: 인제스터 (알림 → 저장, 재파싱)

**Files:**
- Create: `ingest/Ingestor.kt`
- Test: `ingest/IngestorTest.kt` (Robolectric + in-memory DB)

**Interfaces:**
- Consumes: Task 5 `CardNotificationParser`, `PARSER_VERSION`; Task 7 `isSameTransaction`, `findOriginal`; Task 8 저장소
- Produces:
```kotlin
enum class IngestResult { INSERTED, DUPLICATE, UNPARSED, IGNORED }
fun NotificationMessage.contentHash(): String   // SHA-256 hex of "$packageName\u0000${title.orEmpty()}\u0000$body" (postedAt 제외)
class Ingestor(private val repo: SpendingRepository, private val now: () -> LocalDateTime = LocalDateTime::now) {
    suspend fun ingest(message: NotificationMessage): IngestResult
    suspend fun ingestAll(messages: List<NotificationMessage>): List<IngestResult>
    suspend fun reparsePending(): Int   // 새로 거래가 된 건수
}
```
`Parsed` → `findSameKey` 결과 중 `isSameTransaction`이 하나라도 참이면 DUPLICATE. 아니면 취소일 때 `approvalsForCancel` + `findOriginal`로 `originalId`를 채워 `insertNotificationTx`(source NOTIFICATION, sourcePackage, rawText, createdAt = now()). `Candidate` → `insertUnparsedIfNew`(parserVersion = `PARSER_VERSION`). `reparsePending`: 파싱되면 위와 같이 저장하고 `dismissUnparsed`, 아니면 `updateUnparsedVersion(PARSER_VERSION)`.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
@RunWith(RobolectricTestRunner::class)
class IngestorTest {
    @Test fun lotteSample_insertedOnce()          // ingest 두 번 → [INSERTED, DUPLICATE], 거래 1건
    @Test fun samePaymentFromTwoApps_deduped()    // 하나 샘플을 "com.hanacard.app"과 "com.kakao.talk"으로 → 두 번째 DUPLICATE
    @Test fun sameMinuteDifferentCumulative_bothInserted()  // 누적 532,231 / 542,131 → 둘 다 INSERTED
    @Test fun softDeleted_doesNotReappear()       // 저장 → deleteTransaction → 같은 메시지 → DUPLICATE, observeMonth 비어 있음
    @Test fun cancel_linksOriginal()              // 롯데 승인 후 "11,000원 승인취소" 메시지 → 취소의 originalId == 승인 id
    @Test fun unknownCardFormat_storedOnceAsUnparsed()  // 제목 "하나카드" 미지 형식 두 번 → [UNPARSED, UNPARSED], 행 1개, parserVersion == PARSER_VERSION
    @Test fun privateChat_ignored_writesNothing() // IGNORED, 거래·인식 실패 모두 0건
    @Test fun kakaoRepost_insertsEachOnce()       // ingestAll([m1]) 후 ingestAll([m1, m2]) → 거래 2건
    @Test fun reparsePending_convertsAndDismisses() {
        // parserVersion 0으로 롯데 샘플 행과 "알 수 없는 문구" 행 삽입
        // → reparsePending() == 1, 거래 1건, 샘플 행은 dismissed, 다른 행은 parserVersion == PARSER_VERSION이고 observeUnparsed에 남음
    }
}
```

- [ ] **Step 2: 실패 확인** — `--tests "*IngestorTest"`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: Commit** — `feat: 알림 인제스트, 중복 제거, 재파싱`

---

### Task 10: 알림 리스너와 앱 컨테이너

**Files:**
- Create: `notification/NotificationExtractor.kt`, `notification/CardNotificationListener.kt`, `notification/DeviceStatus.kt`, `AppContainer.kt`, `CardSpendingApp.kt`
- Modify: `AndroidManifest.xml`
- Test: `notification/NotificationExtractorTest.kt` (Robolectric)

**Interfaces:**
- Produces:
```kotlin
object NotificationExtractor {
    fun extract(packageName: String, notification: Notification, postTime: Long, zone: ZoneId = ZoneId.systemDefault()): List<NotificationMessage>
}
object DeviceStatus {
    fun isListenerEnabled(context: Context): Boolean          // NotificationManagerCompat.getEnabledListenerPackages
    fun isIgnoringBatteryOptimizations(context: Context): Boolean
}
class AppContainer(context: Context) {
    val database: AppDatabase
    val repository: SpendingRepository      // onChanged는 Task 17 전까지 {}
    val ingestor: Ingestor
    val appScope: CoroutineScope            // SupervisorJob() + Dispatchers.IO
}
class CardSpendingApp : Application() { lateinit var container: AppContainer }
```
추출 규칙: `NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification`이 메시지를 주면 메시지마다 하나(제목 = `EXTRA_TITLE` 없으면 conversationTitle, postedAt = 메시지 timestamp가 0보다 크면 그것, 아니면 postTime). 아니면 `EXTRA_BIG_TEXT ?: EXTRA_TEXT` 하나. 본문이 비어 있으면 빈 목록.
리스너: 자기 패키지 알림은 건너뛴다. `onNotificationPosted`에서 `appScope.launch { ingestor.ingestAll(...) }`. `onListenerDisconnected`에서 `requestRebind(ComponentName(this, CardNotificationListener::class.java))`.
`CardSpendingApp.onCreate`: 컨테이너 생성 후 `appScope.launch { ingestor.reparsePending() }`.
매니페스트: `<application android:name=".CardSpendingApp">`, 서비스
```xml
<service android:name=".notification.CardNotificationListener"
    android:exported="true"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter><action android:name="android.service.notification.NotificationListenerService" /></intent-filter>
</service>
```

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
@RunWith(RobolectricTestRunner::class)
class NotificationExtractorTest {
    @Test fun bigText_usesTitleAndBigText()     // NotificationCompat.Builder 제목 "(결제) 9,900원", BigTextStyle(하나 본문) → 메시지 1개, 제목/본문 일치, postedAt = postTime 변환값
    @Test fun messagingStyle_oneMessagePerEntry() // 메시지 2개 → 2개, 본문 순서 유지
    @Test fun plainText_usesText()              // EXTRA_TEXT만 → 1개
    @Test fun empty_returnsEmpty()              // 본문 없음 → 빈 목록
}
```

- [ ] **Step 2: 실패 확인** — `--tests "*NotificationExtractorTest"`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인 + 빌드** — `./gradlew :app:testDebugUnitTest :app:assembleDebug` / Expected: BUILD SUCCESSFUL
- [ ] **Step 5: Commit** — `feat: 알림 리스너와 앱 컨테이너`

---

### Task 11: 화면 골격과 메인 화면

**Files:**
- Create: `ui/theme/Theme.kt`, `ui/Format.kt`, `ui/IssuerLogo.kt`, `ui/AppNavHost.kt`, `ui/main/MainViewModel.kt`, `ui/main/MainScreen.kt`, `res/drawable-nodpi/logo_lotte.png`, `res/drawable-nodpi/logo_hana.png`
- Modify: `MainActivity.kt`
- Test: `ui/FormatTest.kt`, `ui/main/MainViewModelTest.kt` (Robolectric, `Dispatchers.setMain(UnconfinedTestDispatcher())`)

**Interfaces:**
- Consumes: Task 7 `summarizeMonth`, Task 8 저장소, Task 10 `DeviceStatus`
- Produces:
```kotlin
fun formatWon(amount: Long): String            // 523400 → "523,400원", -20000 → "-20,000원"
fun Issuer.logoRes(): Int                        // R.drawable.logo_lotte / logo_hana
// 내비게이션 경로(@Serializable): MainRoute, CardsRoute, CardEditRoute(cardId: Long? = null),
// CardDetailRoute(cardId: Long, month: String /* "2026-11" */), EntryRoute(cardId: Long? = null, unparsedId: Long? = null, cancel: Boolean = false),
// UnparsedRoute, SetupRoute, SettingsRoute
data class CardRow(val cardId: Long, val issuer: Issuer, val nickname: String, val mask: String, val payment: Long, val recognized: Long)
data class MainUiState(val month: YearMonth, val rows: List<CardRow>, val unregisteredCount: Int, val unparsedCount: Int,
                       val listenerEnabled: Boolean, val backupWarning: String?)
class MainViewModel(repo: SpendingRepository, isListenerEnabled: () -> Boolean, backupWarning: Flow<String?>,
                    initialMonth: YearMonth = YearMonth.now()) : ViewModel() {
    val state: StateFlow<MainUiState>
    fun previousMonth(); fun nextMonth(); fun refreshDeviceStatus()
}
```
로고: 아래 주소에서 받아 그대로 넣는다(롯데 파일은 확장자가 .ico지만 실제 PNG).
`curl -L -o app/src/main/res/drawable-nodpi/logo_lotte.png https://image.lottecard.co.kr/webapp/pc/images/favicon/favicon.ico`
`curl -L -o app/src/main/res/drawable-nodpi/logo_hana.png https://www.hanacard.co.kr/images/common/favicon-192.png`
메인 화면(스펙 5.1 스케치): 상단 `2026년 11월 ◀ ▶`, 경고 배너(알림 권한 꺼짐 → SetupRoute, 인식 실패 N건 → UnparsedRoute, 미등록 카드 결제 N건, 백업 경고 → SettingsRoute), 카드 행(로고 40dp, 별칭, 마스크, `결제`, `실적인정`) → CardDetailRoute. 상단 메뉴: 카드 관리, 설정. 화면이 다시 보일 때(`LifecycleResumeEffect`) `refreshDeviceStatus()`. 이 Task에서는 아직 없는 화면 경로는 빈 `Text` 자리표시로 둔다(Task 12~17에서 채움). `backupWarning`은 Task 17 전까지 `flowOf(null)`을 넘긴다.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
class FormatTest {
    @Test fun formatWon_positive() { assertEquals("523,400원", formatWon(523400)) }
    @Test fun formatWon_negative() { assertEquals("-20,000원", formatWon(-20000)) }
    @Test fun formatWon_zero() { assertEquals("0원", formatWon(0)) }
}
@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {
    @Test fun rows_showMonthTotals()       // 카드 {TAX} + 11월 승인 10,000 "편의점", 50,000 "국세" → row.payment 60,000, recognized 10,000
    @Test fun previousMonth_switchesData() // 10월 승인 5,000 → previousMonth() 후 month 2026-10, payment 5,000
    @Test fun counts_unparsedAndUnregistered()  // 인식 실패 1행 + 미등록 마스크 거래 1건 → 1, 1
    @Test fun backupWarning_passedThrough()     // flowOf("백업 경고") → state.backupWarning == "백업 경고"
}
```

- [ ] **Step 2: 실패 확인** — `--tests "*FormatTest" --tests "*MainViewModelTest"`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인 + 빌드**
- [ ] **Step 5: 에뮬레이터 확인** — `Medium_Phone_API_35` AVD에서 `./gradlew :app:installDebug` 후 실행. 빈 상태 메인 화면과 "알림 권한 꺼짐" 배너가 보이면 통과.
- [ ] **Step 6: Commit** — `feat: 메인 화면과 내비게이션`

---

### Task 12: 카드 관리

**Files:**
- Create: `ui/cards/CardListScreen.kt`, `ui/cards/CardEditScreen.kt`, `ui/cards/CardEditViewModel.kt`
- Test: `ui/cards/CardEditViewModelTest.kt` (Robolectric)

**Interfaces:**
- Produces:
```kotlin
data class CardEditState(val issuer: Issuer = Issuer.LOTTE, val last4: String = "", val nickname: String = "",
                         val exclusions: Set<ExclusionItem> = emptySet(), val conflictWarning: String? = null, val canSave: Boolean = false)
class CardEditViewModel(repo: SpendingRepository, cardId: Long?) : ViewModel() {
    val state: StateFlow<CardEditState>
    fun setIssuer(v: Issuer); fun setLast4(v: String); fun setNickname(v: String); fun toggleExclusion(item: ExclusionItem)
    suspend fun save(): Boolean; suspend fun delete()
}
```
`canSave` = `isValidLast4(last4)`. 별칭이 비어 있으면 저장 시 `issuer.displayName`. 충돌 경고 문구(그대로): `"「${other.nickname}」과 끝 4자리의 1·3번째 숫자가 같아요. 알림으로는 두 카드를 구분할 수 없어요."` 경고가 있어도 저장은 된다. 목록 화면: 카드 행(로고·별칭·마스크) → 편집, 추가 버튼. 편집 화면: 카드사 선택, 끝 4자리(숫자 키패드, 4자 제한), 별칭, 미인정 항목 체크박스 7개(`label`), 저장, (기존 카드면) 삭제.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
@RunWith(RobolectricTestRunner::class)
class CardEditViewModelTest {
    @Test fun invalidLast4_cannotSave()          // "12a4" → canSave false
    @Test fun conflict_showsWarning_butSaves()   // 기존 LOTTE "7512"(별칭 "텔로") + 신규 LOTTE "7913" → 경고 문구에 "텔로", save() == true
    @Test fun save_persistsExclusions()          // TAX, INSTALLMENT 토글 → 저장된 카드 exclusions 일치
    @Test fun blankNickname_defaultsToIssuerName()  // HANA, 별칭 "" → "하나카드"
    @Test fun existingCard_loadsValues()         // cardId로 열면 state가 저장값
}
```

- [ ] **Step 2: 실패 확인** — `--tests "*CardEditViewModelTest"`
- [ ] **Step 3: 구현 (ViewModel + 두 화면 + AppNavHost 연결)**
- [ ] **Step 4: 통과 확인 + 빌드**
- [ ] **Step 5: 에뮬레이터 확인** — 카드 2장 등록 후 메인에 0원 행 2개가 보이면 통과
- [ ] **Step 6: Commit** — `feat: 카드 등록·수정·삭제`

---

### Task 13: 카드 결제 내역

**Files:**
- Create: `ui/detail/CardDetailViewModel.kt`, `ui/detail/CardDetailScreen.kt`
- Test: `ui/detail/CardDetailViewModelTest.kt` (Robolectric)

**Interfaces:**
- Produces:
```kotlin
data class TxItem(val tx: Transaction, val signedAmount: Long, val recognized: Boolean, val label: String)
data class CardDetailState(val card: Card?, val month: YearMonth, val items: List<TxItem>, val payment: Long, val recognized: Long)
class CardDetailViewModel(repo: SpendingRepository, cardId: Long, month: YearMonth) : ViewModel() {
    val state: StateFlow<CardDetailState>
    suspend fun setRecognition(txId: Long, value: Boolean?)   // null = 규칙대로
    suspend fun delete(txId: Long)
}
```
라벨(그대로): 규칙 인정 `"인정"`, 항목 제외 `"미인정 · ${item.label}"`, 직접 지정 `"직접 지정 · 인정"` / `"직접 지정 · 미인정"`. 목록은 `approvedAt` 내림차순, 취소는 음수 금액. 항목을 누르면 선택 대화상자(규칙대로 / 인정 / 미인정 / 삭제). 하단 버튼: `결제 추가` → `EntryRoute(cardId)`, `취소 추가` → `EntryRoute(cardId, cancel = true)`.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
@RunWith(RobolectricTestRunner::class)
class CardDetailViewModelTest {
    @Test fun excludedItem_labelAndTotals()     // {TAX}, "국세" 50,000 + "편의점" 10,000 → 라벨 "미인정 · 세금", payment 60,000, recognized 10,000
    @Test fun manualRecognition_updatesLabelAndTotals()  // "국세"를 true로 → "직접 지정 · 인정", recognized 60,000
    @Test fun resetToRule_restoresRuleLabel()   // 다시 null → "미인정 · 세금"
    @Test fun delete_removesItem()
    @Test fun cancel_hasNegativeSignedAmount()  // 취소 3,000 → signedAmount -3,000
}
```

- [ ] **Step 2~4: 실패 확인 → 구현 → 통과 확인 + 빌드** (`--tests "*CardDetailViewModelTest"`)
- [ ] **Step 5: Commit** — `feat: 카드 결제 내역과 건별 인정 변경`

---

### Task 14: 결제 직접 추가와 인식 실패 목록

**Files:**
- Create: `ui/entry/ManualEntryViewModel.kt`, `ui/entry/ManualEntryScreen.kt`, `ui/unparsed/UnparsedScreen.kt`
- Test: `ui/entry/ManualEntryViewModelTest.kt` (Robolectric)

**Interfaces:**
- Produces:
```kotlin
data class EntryState(
    val cards: List<Card>, val cardId: Long?, val cancel: Boolean, val amountText: String, val merchant: String,
    val date: LocalDate, val time: LocalTime, val installment: Boolean,
    val originalCandidates: List<Transaction>, val originalId: Long?, val rawText: String?, val canSave: Boolean,
)
class ManualEntryViewModel(repo: SpendingRepository, cardId: Long?, unparsedId: Long?, cancel: Boolean,
                           now: () -> LocalDateTime = LocalDateTime::now) : ViewModel() {
    val state: StateFlow<EntryState>
    fun setCard(id: Long); fun setCancel(v: Boolean); fun setAmount(text: String); fun setMerchant(v: String)
    fun setDate(v: LocalDate); fun setTime(v: LocalTime); fun setInstallment(v: Boolean); fun setOriginal(id: Long?)
    suspend fun save(): Boolean
}
```
저장 거래: `source = MANUAL`, issuer·mask = 선택 카드(`maskOf(last4)`), `approvedAt = date.atTime(time)`(분 단위), `originalId`(취소일 때만), `createdAt = now()`. `canSave` = 카드 선택됨 && 금액 > 0 && 가맹점 비어 있지 않음. 취소 후보 = `approvalsForCancel(issuer, mask, approvedAt, amount)`, "원래 결제 없음" 선택 가능. `unparsedId`가 있으면 원문을 화면 위에 보여주고 날짜·시각 기본값 = 그 알림의 `postedAt`, 저장 후 `dismissUnparsed`.
인식 실패 화면: `observeUnparsed()` 목록(받은 시각, 앱 패키지, 제목, 본문 원문). 항목 버튼 `결제로 등록` → `EntryRoute(unparsedId = id)`, `취소로 등록` → `EntryRoute(unparsedId = id, cancel = true)`, `무시` → `dismissUnparsed`.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
@RunWith(RobolectricTestRunner::class)
class ManualEntryViewModelTest {
    @Test fun saveApproval_usesCardMaskAndManualSource()  // 카드 LOTTE 7512, 12,000 "식당" → 거래 mask "7*1*", source MANUAL, kind APPROVAL
    @Test fun saveCancel_linksSelectedOriginal()          // 승인 11,000 존재 → cancel, 5,000, setOriginal(그 id) → originalId 일치
    @Test fun originalCandidates_excludeSmallerAmounts()  // 승인 3,000과 11,000 → 취소 5,000 후보는 11,000만
    @Test fun fromUnparsed_prefillsAndDismisses()         // 인식 실패 행(postedAt 11/18 17:30) → date/time 기본값 일치, 저장 후 observeUnparsed 비어 있음
    @Test fun invalidInput_cannotSave()                   // 금액 "0" 또는 가맹점 "" → canSave false
}
```

- [ ] **Step 2~4: 실패 확인 → 구현 → 통과 확인 + 빌드** (`--tests "*ManualEntryViewModelTest"`)
- [ ] **Step 5: Commit** — `feat: 결제 직접 추가와 인식 실패 목록`

---

### Task 15: 권한 안내 화면

**Files:**
- Create: `ui/setup/SetupScreen.kt`
- Modify: `AndroidManifest.xml`(`<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />`)

**Interfaces:**
- Consumes: Task 10 `DeviceStatus`

화면 구성: (1) 알림 접근 — 상태(`켜짐`/`꺼짐`) + `설정 열기`(`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`). (2) 배터리 최적화 제외 — 상태 + `요청`(`Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `package:` URI). (3) 안내 문구(스펙 8장): 카카오톡 알림과 미리보기 켜기, 카카오톡 'PC 버전 사용 중 모바일 알림 끄기'류 옵션 끄기, 하나카드 앱 결제 푸시 켜기, APK로 설치했다면 앱 정보에서 '제한된 설정 허용'. 화면에 돌아올 때 상태를 다시 읽는다.

- [ ] **Step 1: 구현 + 빌드** — `./gradlew :app:assembleDebug` / Expected: BUILD SUCCESSFUL
- [ ] **Step 2: 에뮬레이터 확인** — 메인 배너 → 권한 안내 → `설정 열기`로 알림 접근 허용 → 돌아오면 `켜짐`, 메인 배너 사라짐
- [ ] **Step 3: Commit** — `feat: 권한 안내 화면`

---

### Task 16: 백업 형식과 Drive 클라이언트

**Files:**
- Create: `backup/BackupData.kt`, `backup/DriveApi.kt`, `backup/DriveClient.kt`
- Test: `backup/BackupCodecTest.kt`, `backup/DriveClientTest.kt` (MockWebServer3, Robolectric 불필요)

**Interfaces:**
- Produces:
```kotlin
@Serializable data class BackupData(val schemaVersion: Int = 1, val exportedAt: String,
    val cards: List<Card>, val transactions: List<Transaction>, val unparsed: List<UnparsedNotification>)
object BackupCodec { fun encode(data: BackupData): String; fun decode(json: String): BackupData }  // Json { ignoreUnknownKeys = true; encodeDefaults = true }

data class DriveFile(val id: String, val name: String, val createdTime: String)
class DriveHttpException(val code: Int, message: String) : IOException(message)
interface DriveApi {
    suspend fun listBackups(token: String): List<DriveFile>      // createdTime 내림차순
    suspend fun upload(token: String, name: String, content: String): DriveFile
    suspend fun download(token: String, fileId: String): String
    suspend fun delete(token: String, fileId: String)
    suspend fun fetchEmail(token: String): String
}
class DriveClient(private val http: OkHttpClient = OkHttpClient(),
                  private val baseUrl: HttpUrl = "https://www.googleapis.com/".toHttpUrl()) : DriveApi
```
요청 형식: 모든 요청 `Authorization: Bearer <token>`, 2xx가 아니면 `DriveHttpException`. 목록 `GET drive/v3/files?spaces=appDataFolder&fields=files(id,name,createdTime)&orderBy=createdTime desc&pageSize=100`. 업로드 `POST upload/drive/v3/files?uploadType=multipart&fields=id,name,createdTime`, `multipart/related` 본문 = 메타데이터 JSON `{"name":"<name>","parents":["appDataFolder"]}` + `application/json` 내용. 다운로드 `GET drive/v3/files/<id>?alt=media`. 삭제 `DELETE drive/v3/files/<id>`. 이메일 `GET oauth2/v3/userinfo` 응답의 `email`. 네트워크 호출은 `Dispatchers.IO`.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
class BackupCodecTest {
    @Test fun roundTrip_preservesEverything()   // 카드 1, 거래 2(취소의 originalId 포함), 인식 실패 1 → decode(encode(x)) == x
    @Test fun unknownField_ignored()            // JSON에 "futureField": 1 추가 → decode 성공
    @Test fun missingOptionalField_usesDefault()// 거래 JSON에서 "deleted" 키 제거 → deleted == false
}
class DriveClientTest {
    @Test fun listBackups_sendsQueryAndParses() // 요청 경로 /drive/v3/files, spaces=appDataFolder, Bearer t → 파일 2개 파싱
    @Test fun upload_putsFileInAppDataFolder()  // 본문에 "\"parents\":[\"appDataFolder\"]"와 내용 문자열 포함, uploadType=multipart
    @Test fun download_returnsBody()            // /drive/v3/files/abc?alt=media → "{...}"
    @Test fun delete_sendsDelete()              // 메서드 DELETE, 경로 /drive/v3/files/abc
    @Test fun unauthorized_throwsWithCode()     // 401 → DriveHttpException(code = 401)
    @Test fun fetchEmail_readsUserinfo()        // /oauth2/v3/userinfo {"email":"a@b.c"} → "a@b.c"
}
```

- [ ] **Step 2~4: 실패 확인 → 구현 → 통과 확인** (`--tests "com.jstensix.cardspending.backup.*"`)
- [ ] **Step 5: Commit** — `feat: 백업 형식과 Drive 클라이언트`

---

### Task 17: 백업 관리, 구글 연결, 설정 화면, 복원

**Files:**
- Create: `backup/BackupPrefs.kt`, `backup/BackupManager.kt`, `backup/DriveAuth.kt`, `backup/BackupWorker.kt`, `backup/BackupScheduler.kt`, `ui/settings/SettingsViewModel.kt`, `ui/settings/SettingsScreen.kt`
- Modify: `gradle/libs.versions.toml`(+`kotlinx-coroutines-play-services` 1.11.0), `app/build.gradle.kts`, `AppContainer.kt`(onChanged → `BackupScheduler.request`, 백업 객체 생성), `ui/AppNavHost.kt`(MainViewModel에 `prefs.observeWarning()` 전달, SettingsRoute 연결), `AndroidManifest.xml`(`android:allowBackup="false"`, `dataExtractionRules`·`fullBackupContent` 속성 제거, `INTERNET` 권한)
- Delete: `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`
- Test: `backup/BackupManagerTest.kt`, `backup/BackupPrefsTest.kt` (Robolectric)

**Interfaces:**
- Consumes: Task 8 `snapshot`/`replaceAll`/`isEmpty`, Task 16 전부
- Produces:
```kotlin
class BackupPrefs(context: Context) {   // SharedPreferences "backup"
    var email: String?; var lastSuccessAt: LocalDateTime?; var lastError: String?
    fun clear()
    fun observeWarning(): Flow<String?>
}
sealed interface TokenResult { data class Token(val value: String) : TokenResult; data object NotConnected : TokenResult; data object NeedsReconnect : TokenResult }
interface TokenProvider { suspend fun accessToken(): TokenResult }
sealed interface ConnectStep { data class NeedsUi(val pendingIntent: PendingIntent) : ConnectStep; data class Granted(val accessToken: String) : ConnectStep }
sealed interface BackupResult { data object Success : BackupResult; data object NotConnected : BackupResult; data object NeedsReconnect : BackupResult; data class Failed(val message: String) : BackupResult }
class BackupManager(repo: SpendingRepository, drive: DriveApi, tokens: TokenProvider, prefs: BackupPrefs,
                    now: () -> LocalDateTime = LocalDateTime::now) {
    suspend fun backupNow(): BackupResult
    suspend fun hasRemoteBackup(): Boolean
    suspend fun restoreLatest(): BackupResult
}
class DriveAuth(context: Context, prefs: BackupPrefs) : TokenProvider {
    companion object { val SCOPES = listOf(Scope("https://www.googleapis.com/auth/drive.appdata"), Scope("email")) }
    suspend fun beginConnect(activity: Activity): ConnectStep   // NeedsUi(pendingIntent) | Granted(accessToken)
    fun tokenFromResult(activity: Activity, data: Intent?): String?
}
object BackupScheduler { fun request(context: Context); fun cancel(context: Context) }
```
문구(그대로): 미연결 경고 `"구글 드라이브 백업이 연결되지 않았어요"`, 재연결 `"구글 계정을 다시 연결해야 해요"`, 실패 `"백업에 실패했어요: <message>"`. `observeWarning()` = 이메일 없음 → 미연결 문구, `lastError` 있음 → 그 문구, 아니면 null.
`backupNow`: 토큰 → `upload("backup-yyyyMMdd-HHmmss.json", encode(snapshot))` → `listBackups` 결과에서 3번째 이후 삭제 → `lastSuccessAt = now()`, `lastError = null`. **업로드가 성공하기 전에는 아무것도 지우지 않는다.** NeedsReconnect/예외 시 `lastError`만 기록. `restoreLatest`: 최신 파일 `download` → `decode` → `replaceAll`.
`DriveAuth.accessToken()`: 이메일 없으면 NotConnected. `Identity.getAuthorizationClient(context).authorize(AuthorizationRequest.builder().setRequestedScopes(SCOPES).setAccount(Account(email, "com.google")).build()).await()` → `hasResolution()`이면 NeedsReconnect, 아니면 Token.
`BackupWorker`(CoroutineWorker): `Failed` → `runAttemptCount < 3`이면 `Result.retry()`, 그 외 모두 `Result.success()`. `BackupScheduler.request`: 이메일이 있을 때만 `enqueueUniqueWork("drive-backup", REPLACE, OneTimeWorkRequest, 네트워크 연결 조건, 초기 지연 30초)`.
설정 화면: 연결 계정(없으면 `구글 계정 연결` 버튼), 마지막 백업 시각, 경고, `지금 백업`, `드라이브에서 복원`(덮어쓰기 확인 대화상자), `연결 해제`(prefs.clear + scheduler.cancel). 연결 흐름: `beginConnect` → NeedsUi면 `rememberLauncherForActivityResult(StartIntentSenderForResult)` → `tokenFromResult` → `drive.fetchEmail(token)` → `prefs.email` 저장 → `repo.isEmpty() && manager.hasRemoteBackup()`이면 "드라이브에 백업이 있어요. 복원할까요?" 대화상자, 아니면 `BackupScheduler.request`.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {
    // FakeDriveApi(파일 목록을 메모리에 보관, upload 실패 스위치), FakeTokenProvider(반환할 TokenResult 지정), now = 2026-11-18T11:43:00
    @Test fun backupNow_uploadsSnapshotAndRecordsSuccess()  // 파일명 "backup-20261118-114300.json", 내용에 카드 별칭 포함, lastSuccessAt 설정, lastError null
    @Test fun backupNow_keepsTwoNewest()                    // 기존 3개 + 새 1개 → 남은 파일 = 가장 새로운 2개
    @Test fun needsReconnect_doesNotUpload_setsWarning()    // 업로드 0회, 결과 NeedsReconnect, lastError == "구글 계정을 다시 연결해야 해요"
    @Test fun uploadFailure_keepsExistingBackups()          // 기존 2개, upload가 IOException → 결과 Failed, 기존 2개 그대로
    @Test fun restoreLatest_replacesLocalData()             // 원격 최신 파일의 카드·거래(originalId 연결 포함)로 교체, 이전 로컬 데이터 없음
}
@RunWith(RobolectricTestRunner::class)
class BackupPrefsTest {
    @Test fun warning_notConnected()   // email null → "구글 드라이브 백업이 연결되지 않았어요"
    @Test fun warning_lastError()      // email 있음 + lastError "x" → "x"
    @Test fun warning_none()           // email 있음 + lastError null → null
}
```

- [ ] **Step 2~4: 실패 확인 → 구현 → 통과 확인 + 빌드** (`--tests "*BackupManagerTest" --tests "*BackupPrefsTest"`, 이어서 `./gradlew :app:testDebugUnitTest :app:assembleDebug`)
- [ ] **Step 5: Commit** — `feat: 구글 드라이브 백업과 복원`

---

### Task 18: Google Cloud 설정 안내와 실기기 검증

**Files:**
- Create: `docs/google-cloud-setup.md`, `docs/device-verification.md`

- [ ] **Step 1: `docs/google-cloud-setup.md` 작성 (한국어 단계별)**
  1. `./gradlew signingReport`로 debug 변형의 SHA-1 확인
  2. console.cloud.google.com → 새 프로젝트
  3. API 및 서비스 → 라이브러리 → Google Drive API 사용 설정
  4. Google 인증 플랫폼(OAuth 동의 화면) → 대상 '외부', 앱 이름·지원 이메일 입력
  5. 데이터 액세스(범위)에 `.../auth/drive.appdata`, `email` 추가
  6. 대상 → **앱 게시('프로덕션'으로 푸시)**. 테스트 상태로 두면 7일마다 권한이 만료된다는 경고를 굵게
  7. 클라이언트 → 클라이언트 만들기 → Android → 패키지 `com.jstensix.cardspending`, 1번의 SHA-1
  8. PC를 바꾸면 새 SHA-1로 Android 클라이언트를 하나 더 만든다(스펙 8장)
- [ ] **Step 2: `docs/device-verification.md` 체크리스트 작성** — 아래 항목과 결과 칸
  - Android Studio로 실기기 설치, 권한 안내 화면에서 알림 접근·배터리 최적화 제외
  - 카드 2장 등록(롯데·하나, 실제 끝 4자리)
  - 실제 롯데 결제 1건 → 메인 금액 반영, 카드 내역 원문 확인 (스펙 9장: 카톡 알림 데이터 구조 검증)
  - 실제 하나 결제 1건 → 반영, 앱 푸시와 카톡이 둘 다 왔다면 1건만
  - 인식 실패 목록에 쌓인 알림이 있으면 원문을 개발자에게 전달(새 파서 추가용)
  - 구글 계정 연결 → `지금 백업` → 마지막 백업 시각 표시
  - 앱 삭제 후 재설치 → 계정 연결 → 복원 제안 → 데이터 복구
- [ ] **Step 3: 사용자와 함께 체크리스트 수행** — 결과를 문서에 기록. 실패 항목은 새 작업으로 남긴다.
- [ ] **Step 4: Commit** — `docs: Google Cloud 설정 안내와 실기기 검증 결과`
