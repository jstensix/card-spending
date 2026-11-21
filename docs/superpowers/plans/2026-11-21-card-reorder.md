# 카드 순서 바꾸기 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 카드 탭에서 카드를 길게 눌러 끌어 순서를 바꾸고, 그 순서를 카드 목록을 쓰는 모든 곳에 적용한다.

**Architecture:** `Card`에 `sortOrder` 열을 더하고(DB 2→3, 기존 행은 `sortOrder = id`), 카드 조회를 `ORDER BY sortOrder, id`로 바꾼다. 순서 유지·저장 규칙은 `SpendingRepository`가 맡는다. 카드 탭은 `sh.calvin.reorderable` 라이브러리로 길게 눌러 끌기를 하고, 놓을 때 한 번 저장한다.

**Tech Stack:** Kotlin, Room 2.8.5, Compose BOM 2026.09, `sh.calvin.reorderable:reorderable:3.1.0`, Robolectric 단위 테스트.

**Spec:** `docs/superpowers/specs/2026-11-21-card-reorder-design.md`

## Global Constraints

- 업데이트 직후 기존 카드 순서가 바뀌면 안 된다(마이그레이션은 `sortOrder = id`).
- 카드를 수정·저장해도 자리는 그대로, 새 카드는 맨 아래.
- 짧게 누르기는 지금처럼 카드 편집을 연다. 길게 눌러 끌고, 놓을 때 한 번만 저장한다.
- 순서는 카드 탭, 홈 카드 목록, 결제 직접 입력의 카드 선택에 모두 적용된다(모두 `observeCards()`를 쓰므로 조회 순서만 바꾸면 된다).
- 순서가 없는 예전 백업은 등록 순서로 복원된다.
- 커밋은 명시 경로만, 메시지 끝 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

- 편집 화면은 `sortOrder`를 모른다(`toCard`가 기본값 0으로 새 `Card`를 만든다): 수정 저장 후에도 자리 유지. Task 1 `editingCard_keepsPlace`.
- 예전 백업(필드 없음) 복원 후 등록 순서, 그 뒤 새 카드는 맨 아래. Task 1 `oldBackupWithoutSortOrder_decodesZero`, `newCard_goesToBottom`.
- 폰의 v2 DB를 v3로 올려도 카드 순서·결제 그대로. Task 1 `migration2to3_keepsRegistrationOrder`.
- 길게 눌렀다가 움직이지 않고 떼면 순서가 바뀌지 않는다(편집 화면이 열리는지는 기기 확인). Task 2 기기 확인.
- 끄는 중 맨 위/맨 아래로 옮긴 결과가 정확하다. Task 2 `move_*`.

---

### Task 1: 순서 저장

**Files:**
- Modify: `app/src/main/java/com/jstensix/cardspending/model/Card.kt`, `data/AppDatabase.kt`, `data/CardDao.kt`, `data/SpendingRepository.kt`
- Create: `app/schemas/com.jstensix.cardspending.data.AppDatabase/3.json` (KSP가 생성, 커밋)
- Test: `data/MigrationTest.kt`, `data/SpendingRepositoryTest.kt`, `backup/BackupCodecTest.kt`

**Interfaces:**
- Produces: `Card.sortOrder: Int` (마지막 생성자 인자, `@ColumnInfo(defaultValue = "0")`, 기본 0); `AppDatabase.MIGRATION_2_3`; `SpendingRepository.reorderCards(idsInOrder: List<Long>)`.

- [ ] **Step 1: Tests**
  - `MigrationTest`: 기존 테스트의 빌더에 `MIGRATION_2_3`도 추가. 새 `migration2to3_keepsRegistrationOrder`: v1 스키마에 카드 2장(`7512` 텔로, `0381` 심플)을 넣고 `MIGRATION_1_2, MIGRATION_2_3`로 열어 `room.cardDao().all().map { it.id to it.sortOrder } == listOf(1L to 1, 2L to 2)`, 결제 1건 유지.
  - `SpendingRepositoryTest` (카드 A `7512`, B `0381`, C `1234` 순서로 저장):
    - `reorderCards_changesObservedOrder`: `reorderCards(listOf(c, a, b))` 후 `observeCards().first().map { it.id } == listOf(c, a, b)`.
    - `newCard_goesToBottom`: 위 재정렬 뒤 D 저장 → 마지막이 D.
    - `editingCard_keepsPlace`: 위 재정렬 뒤 `saveCard(Card(id = a, issuer = LOTTE, last4 = "7512", nickname = "새이름"))`(sortOrder 기본 0) → 순서 그대로 `[c, a, b]`, A 별칭은 "새이름".
    - `writes_callOnChanged`에 `reorderCards` 한 번 추가 → 호출 횟수 +1(재정렬 한 번 = 알림 한 번).
  - `BackupCodecTest`: `roundTrip_preservesSortOrder`(sortOrder 3인 카드 왕복), `oldBackupWithoutSortOrder_decodesZero`(`,"sortOrder":0` 제거 후 decode == data).
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*MigrationTest" --tests "*SpendingRepositoryTest" --tests "*BackupCodecTest"` → FAIL(컴파일: `sortOrder`, `MIGRATION_2_3`, `reorderCards` 없음)
- [ ] **Step 3: Implement**
  - `MIGRATION_2_3`: `ALTER TABLE cards ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0` 후 `UPDATE cards SET sortOrder = id`. DB `version = 3`, `create()`에 추가.
  - `CardDao`: `observeAll`/`all`을 `ORDER BY sortOrder, id`; `maxSortOrder(): Int?`; `setSortOrder(id: Long, order: Int)`.
  - `saveCard`: `withTransaction` 안에서 새 카드는 `(maxSortOrder() ?: -1) + 1`, 기존 카드는 저장된 행의 `sortOrder`를 덮어쓴다(없으면 들어온 값).
  - `reorderCards`: `withTransaction`으로 목록 순서대로 0..n-1 기록, 끝나고 `onChanged()` 한 번.
- [ ] **Step 4: Run** 같은 명령 → PASS. 그 다음 `./gradlew :app:testDebugUnitTest :app:assembleDebug` → 전체 PASS, `3.json` 생성 확인.
- [ ] **Step 5: Commit** `feat: 카드 순서를 저장하고 수정해도 자리를 유지` (위 파일 + `3.json`)

### Task 2: 카드 탭에서 길게 눌러 끌기

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `ui/cards/CardListScreen.kt`, `ui/AppNavHost.kt`
- Create: `ui/cards/ListMove.kt`
- Test: `ui/cards/ListMoveTest.kt`

**Interfaces:**
- Consumes: Task 1 `SpendingRepository.reorderCards(idsInOrder: List<Long>)`
- Produces: `fun <T> List<T>.move(from: Int, to: Int): List<T>`; `CardListScreen(cards, onEdit, onAdd, onReorder: (List<Long>) -> Unit)`

- [ ] **Step 1: Tests** `ListMoveTest`
  - `move_down`: `listOf("A","B","C").move(0, 2) == listOf("B","C","A")`
  - `move_up`: `listOf("A","B","C").move(2, 0) == listOf("C","A","B")`
  - `move_samePlace_unchanged`: `move(1, 1)` == 원래 목록
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*ListMoveTest"` → FAIL(`move` 없음)
- [ ] **Step 3: Implement**
  - `move`: 원본을 바꾸지 않는 새 목록(`removeAt(from)` 후 `add(to, …)`).
  - 의존성: `reorderable = "3.1.0"`, `reorderable = { group = "sh.calvin.reorderable", name = "reorderable", version.ref = "reorderable" }`, `implementation(libs.reorderable)`.
  - `CardListScreen`: `cards`로 초기화되는 화면 임시 목록을 들고, `rememberReorderableLazyListState`의 이동 콜백에서 **key로 위치를 찾아** `move`로 갱신(빈 목록 안내 항목 때문에 index를 그대로 쓰지 않는다). 각 카드는 `ReorderableItem`으로 감싸고 `WhiteCard`에 `longPressDraggableHandle(onDragStarted = 진동, onDragStopped = { onReorder(임시 목록 id들) })`. 끄는 중인 카드는 떠 보이게(그림자 등, 빌더 재량). 짧게 누르기는 기존 `onEdit` 그대로.
  - `AppNavHost` 카드 탭: `onReorder = { ids -> container.appScope.launch { container.repository.reorderCards(ids) } }`(탭을 떠나도 저장이 취소되지 않게).
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest :app:assembleDebug` → 전체 PASS
- [ ] **Step 5: 기기 확인**(폰 연결 시): 업데이트 직후 순서 그대로, 길게 눌러 끌어 놓으면 카드 탭·홈 순서가 바뀜, 짧게 누르면 편집 열림, 길게 눌렀다 움직이지 않고 떼면 순서 그대로.
- [ ] **Step 6: Commit** `feat: 카드 탭에서 길게 눌러 끌어 순서 바꾸기`
