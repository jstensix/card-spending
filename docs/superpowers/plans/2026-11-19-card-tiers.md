# 카드별 실적 구간과 혜택 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 카드마다 실적 구간(최대 5개, 혜택 글 포함)을 등록하고, 홈 카드 게이지를 구간대로 나눠 달성 여부를 보여주며, 카드 상세에 구간별 혜택을 보여준다.

**Architecture:** `Card`에 `tiers: List<Tier>`를 추가해 `cards` 표의 JSON 문자열 칸에 저장한다(DB v1→v2 마이그레이션). 달성 계산은 순수 함수 `tierProgress`(rules), 화면 문구는 순수 함수(ui/model)로 두고 테스트한다. 게이지는 새 컴포넌트 `TierBar`.

**Tech Stack:** Kotlin, Room 2.8.5, kotlinx.serialization, Jetpack Compose, Robolectric 단위 테스트.

**Spec:** `docs/superpowers/specs/2026-11-19-card-tiers-design.md`

## Global Constraints

- 구간 최대 5개(`MAX_TIERS = 5`). 금액은 양수, 카드 안에서 중복 불가, 금액 오름차순 저장. 혜택 글은 비어도 됨(앞뒤 공백 제거).
- 달성 = 보고 있는 달의 실적인정 금액 ≥ 구간 금액. 실적인정 금액이 0 이하면 0으로 본다.
- 문구 그대로: 섹션 제목 `실적 구간`, 버튼 `+ 구간 추가`, 경고 `구간 금액을 입력해 주세요` / `같은 금액의 구간이 있어요`, 상태 줄 `30만까지 180,000원 남음` / `50만 구간 달성 · 100만까지 380,000원 남음` / `최고 구간 달성`. 남은 금액은 `formatWon`(쉼표, 원 단위 그대로).
- 눈금 라벨: 만 원 단위 `30만`, 나누어떨어지지 않으면 소수 첫째 자리 `12.5만`, 천 단위 쉼표 `1,500만`.
- 새 필드는 기본값 필수(예전 드라이브 백업 호환). DB 칸은 `@ColumnInfo(defaultValue = "[]")`로 마이그레이션의 `DEFAULT '[]'`와 맞춘다.
- 커밋은 명시 경로만 `git add -- <paths>`, `git add -A` 금지. 메시지 끝 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- 전체 테스트 `./gradlew :app:testDebugUnitTest`, 빌드 `./gradlew :app:assembleDebug`.

## Review Focus

- 사용자 폰의 실제 v1 DB가 v2로 올라갈 때 카드·결제가 그대로 남아야 함 → Task 1 `migration1to2_keepsCardsAndAddsEmptyTiers`.
- 구간 기능 전 드라이브 백업(`tiers` 키 없음) 복원 → Task 1 `oldBackupWithoutTiers_decodesEmptyTiers`.
- 취소가 많아 실적인정 금액이 음수인 달 → 달성 0, 남은 금액은 0원 기준 → Task 2 `negativeRecognized_countsAsZero`.
- 정확히 구간 금액만큼 쓴 경우 달성으로 표시 → Task 2 `exactThreshold_isAchieved`.
- 가까운 구간(예: 30만·35만, 최고 300만)의 눈금 라벨이 겹치거나 오른쪽 끝 라벨이 잘림 → Task 4 `tierLabelVisibility_*`.

---

### Task 1: 데이터(구간 모델, DB v2, 백업 호환)

**Files:**
- Create: `app/src/main/java/com/jstensix/cardspending/model/Tier.kt`
- Modify: `model/Card.kt`, `data/Converters.kt`, `data/AppDatabase.kt`
- Create (생성됨): `app/schemas/com.jstensix.cardspending.data.AppDatabase/2.json` — 빌드가 만든 파일을 커밋
- Test: `app/src/test/java/com/jstensix/cardspending/data/SpendingRepositoryTest.kt`, `.../data/MigrationTest.kt`(새 파일), `.../backup/BackupCodecTest.kt`

**Interfaces:**
- Produces: `@Serializable data class Tier(val amount: Long, val benefit: String = "")`
- Produces: `Card.tiers: List<Tier> = emptyList()` (`@ColumnInfo(defaultValue = "[]")`, `exclusions` 다음)
- Produces: `AppDatabase.MIGRATION_1_2: Migration` (companion) — `ALTER TABLE cards ADD COLUMN tiers TEXT NOT NULL DEFAULT '[]'`; `create()`에 등록; `version = 2`
- Produces: `Converters.fromTiers(List<Tier>): String` / `toTiers(String): List<Tier>` (kotlinx Json)

- [ ] **Step 1: Write the failing tests**
  - SpendingRepositoryTest `saveCard_persistsTiers`: `Card(..., tiers = listOf(Tier(300000, "통신"), Tier(500000, "")))` 저장 → `getCard(id)!!.tiers`가 같은 목록.
  - BackupCodecTest `oldBackupWithoutTiers_decodesEmptyTiers`: `BackupCodec.encode(data)`에서 `"tiers":[],` 를 지운 문자열을 decode → `data`와 같음. 그리고 기존 `roundTrip`용 data의 카드에 `tiers = listOf(Tier(300000, "통신비 5천원"))`를 줘도 왕복이 같은지 `roundTrip_preservesTiers`.
  - MigrationTest(Robolectric) `migration1to2_keepsCardsAndAddsEmptyTiers`: 앱 컨텍스트의 `getDatabasePath("migration-test.db")`에 `SQLiteDatabase`로 v1 스키마를 만든다(1.json의 3개 `createSql` + 인덱스 2개, `${TABLE_NAME}` 치환), 카드 1행(`'LOTTE','7512','텔로','TAX'`)과 결제 1행 삽입, `PRAGMA user_version = 1`. 그 파일을 `Room.databaseBuilder(...).addMigrations(AppDatabase.MIGRATION_1_2).allowMainThreadQueries()`로 열어 카드 1개(`nickname == "텔로"`, `tiers` 빈 목록, `exclusions == {TAX}`)와 결제 1개가 있는지 확인. 끝나면 DB 닫고 파일 삭제.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*SpendingRepositoryTest" --tests "*BackupCodecTest" --tests "*MigrationTest"` — Expected: FAIL(컴파일 오류: `Tier`, `tiers`, `MIGRATION_1_2` 없음).
- [ ] **Step 3: Implement** Interfaces 그대로. `version = 2`로 올리면 빌드가 `2.json`을 만든다.
- [ ] **Step 4: Run** 같은 명령 — Expected: PASS. 이어서 전체 `./gradlew :app:testDebugUnitTest` PASS.
- [ ] **Step 5: Commit** — 위 파일 + `2.json`; `feat: 카드 실적 구간 데이터와 DB v2 마이그레이션`

### Task 2: 달성 계산과 화면 문구(순수 함수)

**Files:**
- Create: `app/src/main/java/com/jstensix/cardspending/rules/TierProgress.kt`
- Modify: `app/src/main/java/com/jstensix/cardspending/ui/model/Presentation.kt`
- Test: `app/src/test/java/com/jstensix/cardspending/rules/TierProgressTest.kt`(새), `.../ui/model/PresentationTest.kt`

**Interfaces:**
- Consumes: Task 1 `Tier`
- Produces: `data class TierProgress(val tiers: List<Tier>, val achieved: Int, val fill: Float, val marks: List<Float>, val next: Tier?, val remaining: Long?)` — `marks[i] = tiers[i].amount / 최고 구간`, `fill = max(recognized,0) / 최고 구간` (0~1), `next` = 첫 미달성 구간, `remaining = next.amount - max(recognized,0)`
- Produces: `fun tierProgress(recognized: Long, tiers: List<Tier>): TierProgress?` — `tiers` 비면 null. 입력 `tiers`는 정렬돼 있다고 가정하지 말고 금액순으로 정렬해 쓴다.
- Produces (Presentation.kt): `fun compactManwon(amount: Long): String`, `fun tierStatus(progress: TierProgress): String`

- [ ] **Step 1: Write the failing tests** (구간 30만/50만/100만)

```kotlin
@Test fun noneAchieved() { // 120,000
    val p = tierProgress(120_000, tiers)!!
    assertEquals(0, p.achieved); assertEquals(0.12f, p.fill, 0.0001f)
    assertEquals(listOf(0.3f, 0.5f, 1f), p.marks); assertEquals(300_000L, p.next!!.amount); assertEquals(180_000L, p.remaining)
}
@Test fun partlyAchieved() { // 620,000 → achieved 2, next 1,000,000, remaining 380,000, fill 0.62
}
@Test fun exactThreshold_isAchieved() { // 500,000 → achieved 2
}
@Test fun allAchieved_fullAndNoNext() { // 1,200,000 → achieved 3, fill 1f, next null, remaining null
}
@Test fun negativeRecognized_countsAsZero() { // -5,000 → achieved 0, fill 0f, remaining 300,000
}
@Test fun unsortedTiers_areSorted() { // tiers 100만/30만/50만 순으로 줘도 marks == [0.3,0.5,1.0]
}
@Test fun noTiers_null() { assertNull(tierProgress(100, emptyList())) }
```
  PresentationTest:
  - `compactManwon_formats`: 300000→`30만`, 1000000→`100만`, 125000→`12.5만`, 15000000→`1,500만`
  - `tierStatus_texts`: 위 none/partly/all 진행값으로 각각 `30만까지 180,000원 남음`, `50만 구간 달성 · 100만까지 380,000원 남음`, `최고 구간 달성`
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*TierProgressTest" --tests "*PresentationTest"` — Expected: FAIL(컴파일 오류).
- [ ] **Step 3: Implement** Interfaces 그대로.
- [ ] **Step 4: Run** 같은 명령 — Expected: PASS.
- [ ] **Step 5: Commit** — 4개 파일; `feat: 실적 구간 달성 계산과 표시 문구`

### Task 3: 카드 추가/수정에서 구간 입력

**Files:**
- Modify: `app/src/main/java/com/jstensix/cardspending/ui/cards/CardEditViewModel.kt`
- Modify: `app/src/main/java/com/jstensix/cardspending/ui/cards/CardEditScreen.kt`
- Modify: `app/src/main/java/com/jstensix/cardspending/ui/AppNavHost.kt` (화면에 새 콜백 연결)
- Test: `app/src/test/java/com/jstensix/cardspending/ui/cards/CardEditViewModelTest.kt`

**Interfaces:**
- Consumes: Task 1 `Tier`, `Card.tiers`
- Produces: `data class TierInput(val amountText: String = "", val benefit: String = "")`; `CardEditState.tiers: List<TierInput> = emptyList()`, `CardEditState.tierWarning: String? = null`; `const val MAX_TIERS = 5`
- Produces: `CardEditViewModel.addTier()`(5개면 무시), `removeTier(index: Int)`, `setTierAmount(index: Int, text: String)`(숫자만), `setTierBenefit(index: Int, text: String)`
- `canSave = isValidLast4 && tierWarning == null`; `save()`도 같은 조건이 아니면 false. 저장 시 `tiers = 입력.map { Tier(amount, benefit.trim()) }.sortedBy { it.amount }`. 기존 카드 불러오면 `TierInput(amount.toString(), benefit)`.

- [ ] **Step 1: Write the failing tests**
  - `addTier_capsAtFive`: `addTier()` 6번 → `state.tiers.size == 5`
  - `save_persistsTiersSortedAndTrimmed`: 행 2개 `"500000","커피 "` / `"300000","통신"` → 저장된 카드 `tiers == [Tier(300000,"통신"), Tier(500000,"커피")]`
  - `blankTierAmount_blocksSave`: `addTier()` 후 → `canSave == false`, `tierWarning == "구간 금액을 입력해 주세요"`, `save() == false`; 금액 `"0"`도 같은 경고
  - `duplicateTierAmounts_blockSave`: 두 행 모두 `"300000"` → `tierWarning == "같은 금액의 구간이 있어요"`, `canSave == false`
  - `removeTier_dropsRow`: 행 2개에서 0번 삭제 → 남은 행이 원래 1번
  - `existingCard_loadsTiers`: 구간 있는 카드 저장 후 수정 VM → `state.tiers == [TierInput("300000","통신")]`
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*CardEditViewModelTest"` — Expected: FAIL(컴파일 오류).
- [ ] **Step 3: Implement** VM(Interfaces 그대로). 화면: '실적 미인정 항목' 아래 `SectionLabel("실적 구간")` + 행마다 `BrandTextField`(금액, 숫자 키보드, 천 단위 쉼표 표시) · `BrandTextField`(혜택) · 삭제 아이콘 버튼(`BounceIconButton`), 그 아래 `+ 구간 추가`(5개면 비활성), `tierWarning`은 Ruby 캡션. 천 단위 쉼표는 `ManualEntryScreen`의 private `ThousandsSeparator`를 `ui/components/Inputs.kt`로 옮겨 함께 쓴다.
- [ ] **Step 4: Run** 같은 명령 → PASS, 그리고 `./gradlew :app:testDebugUnitTest :app:assembleDebug` PASS.
- [ ] **Step 5: Commit** — VM·화면·AppNavHost·Inputs.kt·ManualEntryScreen.kt·테스트; `feat: 카드 추가/수정에서 실적 구간과 혜택 입력`

### Task 4: 홈 게이지와 상세 구간 목록

**Files:**
- Modify: `ui/main/MainViewModel.kt` (`CardRow.tiers: List<Tier> = emptyList()` 추가, `row.card.tiers` 전달)
- Modify: `ui/components/Bars.kt` (`TierBar`), `ui/model/Presentation.kt` (`tierLabelVisibility`)
- Modify: `ui/main/MainScreen.kt` (구간 있으면 `TierBar` + `tierStatus` 캡션, 없으면 기존 `RatioBar`)
- Modify: `ui/detail/CardDetailScreen.kt` (요약 카드 아래 `실적 구간` 목록)
- Test: `ui/main/MainViewModelTest.kt`, `ui/model/PresentationTest.kt`

**Interfaces:**
- Consumes: Task 2 `tierProgress`, `TierProgress`, `compactManwon`, `tierStatus`
- Produces: `fun tierLabelVisibility(marks: List<Float>, minGap: Float = 0.12f): List<Boolean>` — 왼쪽부터 직전에 보인 라벨과 `minGap` 미만이면 숨김, 마지막(최고 구간)은 항상 보임; 마지막과 겹치는 직전 라벨은 숨김.
- Produces: `@Composable fun TierBar(progress: TierProgress, modifier: Modifier = Modifier)` — 6dp 트랙(기존 `RatioBar`와 같은 색), `fill`만큼 채움, `marks` 위치에 눈금, 아래에 `compactManwon` 라벨(달성이면 ` ✓`, 달성 라벨은 PrimaryDeep, 아니면 InkMute), 마지막 라벨은 오른쪽 끝 정렬로 잘리지 않게.

- [ ] **Step 1: Write the failing tests**
  - MainViewModelTest `row_carriesCardTiers`: 구간 있는 카드 → `state.rows.single().tiers`가 카드의 구간.
  - PresentationTest `tierLabelVisibility_spacedAllShown`: `[0.3,0.5,1.0]` → `[true,true,true]`; `tierLabelVisibility_closeLabelsHidden`: `[0.1,0.15,1.0]` → `[true,false,true]`; `tierLabelVisibility_nearTopHidden`: `[0.3,0.95,1.0]` → `[true,false,true]`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*MainViewModelTest" --tests "*PresentationTest"` — Expected: FAIL(컴파일 오류).
- [ ] **Step 3: Implement** Interfaces + 화면. 상세 목록 한 줄: `✓`/`○` · `${compactManwon(amount)}원` · 혜택 글(여러 줄 허용), `next`인 줄에만 ` · ${formatWon(remaining)} 남음`.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest :app:assembleDebug` — Expected: PASS.
- [ ] **Step 5: 기기 확인**(폰 연결 시): 앱 업데이트 후 DB 마이그레이션으로 기존 카드·결제가 그대로인지, 카드 수정 화면에서 구간 행 추가/삭제/경고가 보이는지(저장하지 않음). 홈 게이지와 상세 목록은 사용자가 실제 구간을 저장한 뒤 확인을 부탁한다. 못 한 항목은 보고에 적는다.
- [ ] **Step 6: Commit** — 위 파일; `feat: 홈 게이지를 실적 구간대로 표시하고 상세에 구간별 혜택 표시`
