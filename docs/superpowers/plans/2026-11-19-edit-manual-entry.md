# 직접 입력 결제 수정 + 등록 방식 표시 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 직접 입력한 결제를 기존 입력 화면(수정 모드)에서 고칠 수 있게 하고, 결제 줄과 시트에 `자동`/`직접 입력`을 표시한다.

**Architecture:** `ManualEntryViewModel`에 `txId` 수정 모드를 더해 저장된 행을 미리 채우고 같은 행을 갱신한다. 갱신은 `SpendingRepository.updateManual`이 MANUAL 행에만 허용한다. 결제 보조 문구는 순수 함수 `txMeta`로 옮겨 테스트한다. DB 스키마와 백업 형식은 바뀌지 않는다.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Navigation Compose(type-safe routes), Robolectric 단위 테스트.

**Spec:** `docs/superpowers/specs/2026-11-19-edit-manual-entry-design.md`

## Global Constraints

- 화면 문구는 그대로: 수정 모드 제목 `결제 수정`, 시트 버튼 `수정`, 등록 방식 `자동`(NOTIFICATION) / `직접 입력`(MANUAL).
- 알림 결제(`TxSource.NOTIFICATION`)는 수정 불가. 버튼도 숨기고 저장도 거부한다.
- 수정 저장 시 id, `createdAt`, `manualRecognition`, `source`는 유지한다.
- `Transaction`에 필드를 추가하지 않는다(백업 호환).
- 커밋은 명시 경로만 `git add -- <paths>`. `git add -A` 금지. 커밋 메시지 끝: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- 전체 테스트: `./gradlew :app:testDebugUnitTest` / 빌드: `./gradlew :app:assembleDebug`.

## Review Focus

- 수정할 결제의 카드가 삭제됨 → 카드 칸이 비고 저장 불가(사용자가 카드를 골라야 함). Task 2 테스트 `edit_cardGone_leavesCardEmpty`.
- 수정 화면을 열자마자 금액·가맹점 칸이 저장된 값으로 보여야 함. 화면은 `rememberSaveable`로 입력값을 들고 있어서, 불러오기 전에 그리면 빈 칸으로 굳는다 → `loaded` 전에는 입력 칸을 그리지 않는다(Task 3, 기기 확인).
- 수정 중 결제→취소 전환 시 자기 자신이 원 결제 후보에 나오면 안 됨. Task 2 테스트 `edit_excludesSelfFromOriginalCandidates`.
- 알림 결제 id로 수정 화면에 들어와도 저장되면 안 됨. Task 2 테스트 `edit_notificationTx_refusesSave`.
- 취소→결제로 바꾸면 원 결제 연결이 지워져야 함. Task 2 테스트 `edit_cancelToApproval_clearsOriginal`.

---

### Task 1: 결제 보조 문구에 등록 방식 표시

**Files:**
- Modify: `app/src/main/java/com/jstensix/cardspending/ui/model/Presentation.kt`
- Modify: `app/src/main/java/com/jstensix/cardspending/ui/detail/CardDetailScreen.kt` (private `TxItem.meta()` 제거, `txMeta(item.tx)` 사용)
- Test: `app/src/test/java/com/jstensix/cardspending/ui/model/PresentationTest.kt`

**Interfaces:**
- Produces: `fun txMeta(tx: Transaction): String` — `HH:mm · 일시불|할부[ · 취소] · 자동|직접 입력`

- [ ] **Step 1: Write the failing tests** (PresentationTest)

```kotlin
@Test fun txMeta_notificationApproval() {
    val tx = Transaction(issuer = Issuer.LOTTE, kind = TxKind.APPROVAL, amount = 5600, merchant = "카페", mask = "7*1*",
        installment = false, approvedAt = LocalDateTime.of(2026, 11, 18, 14, 32), source = TxSource.NOTIFICATION,
        createdAt = LocalDateTime.of(2026, 11, 18, 14, 32))
    assertEquals("14:32 · 일시불 · 자동", txMeta(tx))
}

@Test fun txMeta_manualInstallmentCancel() {
    val tx = Transaction(issuer = Issuer.HANA, kind = TxKind.CANCEL, amount = 3000, merchant = "마트", mask = "0*8*",
        installment = true, approvedAt = LocalDateTime.of(2026, 11, 18, 9, 5), source = TxSource.MANUAL,
        createdAt = LocalDateTime.of(2026, 11, 18, 9, 5))
    assertEquals("09:05 · 할부 · 취소 · 직접 입력", txMeta(tx))
}
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "com.jstensix.cardspending.ui.model.PresentationTest"` — Expected: FAIL, `Unresolved reference 'txMeta'`.
- [ ] **Step 3: Implement `txMeta`** in Presentation.kt (기존 `TxItem.meta()` 문구 + 끝에 ` · 자동`/` · 직접 입력`), CardDetailScreen의 두 사용처(결제 줄, 시트 보조 문구)를 `txMeta(item.tx)`로 교체하고 private `meta()` 삭제.
- [ ] **Step 4: Run** 같은 명령 — Expected: PASS.
- [ ] **Step 5: Commit** — `git add -- <위 3개 파일>`; `git commit -m "feat: 결제 줄과 시트에 등록 방식(자동/직접 입력) 표시"`

### Task 2: 입력 ViewModel 수정 모드 + 저장소 갱신

**Files:**
- Modify: `app/src/main/java/com/jstensix/cardspending/data/SpendingRepository.kt`
- Modify: `app/src/main/java/com/jstensix/cardspending/ui/entry/ManualEntryViewModel.kt`
- Test: `app/src/test/java/com/jstensix/cardspending/ui/entry/ManualEntryViewModelTest.kt`

**Interfaces:**
- Produces: `suspend fun SpendingRepository.updateManual(tx: Transaction): Boolean` — 저장된 같은 id 행이 있고 `source == MANUAL`이며 `deleted == false`일 때만 `txDao.update(tx.copy(source = MANUAL, createdAt = 저장된 값, manualRecognition = 저장된 값))` 후 `onChanged()`, true. 아니면 아무것도 안 하고 false.
- Produces: `ManualEntryViewModel(repo, cardId: Long?, unparsedId: Long?, cancel: Boolean, txId: Long? = null, now: () -> LocalDateTime = LocalDateTime::now)` (`now`는 마지막 유지: 테스트가 후행 람다로 넘김)
- Produces: `EntryState.editing: Boolean = false`, `EntryState.loaded: Boolean = true` (수정 모드는 행을 불러오기 전까지 false)

- [ ] **Step 1: Write the failing tests** (ManualEntryViewModelTest; `vm()` 헬퍼에 `txId: Long? = null` 추가, 수동 행은 `repo.addManual(...)`로 넣는 `manualTx(...)` 로컬 헬퍼 작성)

```kotlin
// 공통 준비: 2026-11-10 19:30, 12000원, "식당", 할부, 롯데 7*1*, manualRecognition=false, createdAt=2026-11-10 19:31 인 MANUAL 승인 행 → id

@Test fun edit_prefillsAllFields() {
    // vm(txId = id).state.await { it.loaded }
    // editing == true, cardId == cardId, amountText == "12000", merchant == "식당",
    // date == 2026-11-10, time == 19:30, installment == true, cancel == false
}

@Test fun edit_save_updatesSameRowKeepingIdCreatedAtAndRecognition() {
    // setAmount("15000"); setMerchant("식당2"); await { it.canSave && it.amountText == "15000" }; save() == true
    // savedNovember().single(): id == id, amount == 15000, merchant == "식당2",
    // manualRecognition == false, createdAt == 2026-11-10 19:31, source == MANUAL
}

@Test fun edit_cancelToApproval_clearsOriginal() {
    // MANUAL 취소 행(originalId = 알림 승인 id)을 수정 → setCancel(false) → save() == true → 그 행 originalId == null, kind == APPROVAL
}

@Test fun edit_excludesSelfFromOriginalCandidates() {
    // MANUAL 승인 20000원(11-10) + 알림 승인 30000원(11-09). MANUAL 행 수정 → setCancel(true)
    // await { it.cancel && it.originalCandidates.isNotEmpty() } → 후보 id에 MANUAL 행 id 없음, 알림 승인 id 있음
}

@Test fun edit_notificationTx_refusesSave() {
    // 알림 승인 행 id로 vm(txId) → await { it.loaded } → setAmount("1") → save() == false → 저장된 행 amount 그대로
}

@Test fun edit_cardGone_leavesCardEmpty() {
    // MANUAL 행을 하나카드 0*8* 마스크로 저장(등록 카드 없음) → vm(txId) await { it.loaded } → cardId == null, canSave == false
}
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "com.jstensix.cardspending.ui.entry.ManualEntryViewModelTest"` — Expected: FAIL(컴파일 오류: `txId`, `loaded`, `editing` 없음).
- [ ] **Step 3: Implement**
  - `updateManual` (Interfaces 그대로).
  - VM: `txId != null`이면 초기 form을 `editing = true, loaded = false`로 두고, init에서 `repo.getTransaction(txId)`와 `repo.observeCards().first()`로 채운다. 카드는 `issuer == tx.issuer && maskOf(last4) == tx.mask`인 첫 카드(없으면 null). `amountText = amount.toString()`, `cancel = kind == CANCEL`, `date/time` = `approvedAt`, `installment`, `originalId`, `loaded = true`. 불러온 원본은 private 필드에 보관.
  - 원 결제 후보에서 `id == txId` 제외.
  - `save()`: 수정 모드면 원본 `.copy(issuer, kind, amount, merchant.trim(), mask, installment, approvedAt, originalId = 취소일 때만 f.originalId)`를 `repo.updateManual`로 저장하고 그 결과를 반환. 행이 없으면 false. 새 입력 경로는 그대로.
- [ ] **Step 4: Run** 같은 명령 — Expected: PASS(기존 테스트 포함).
- [ ] **Step 5: Commit** — 위 3개 파일; `git commit -m "feat: 직접 입력 결제 수정 모드(ViewModel·저장소)"`

### Task 3: 화면 연결(시트의 수정 버튼, 수정 모드 화면)

**Files:**
- Modify: `app/src/main/java/com/jstensix/cardspending/ui/AppNavHost.kt` (`EntryRoute`에 `val txId: Long = 0`; Entry 화면 VM에 `route.txId.takeIf { it != 0L }` 전달; CardDetail에 `onEdit = { nav.navigate(EntryRoute(txId = it)) }`)
- Modify: `app/src/main/java/com/jstensix/cardspending/ui/detail/CardDetailScreen.kt` (`onEdit: (txId: Long) -> Unit` 파라미터; 시트에서 `item.tx.source == TxSource.MANUAL`일 때만 인정 토글 아래, 삭제 위에 `PillButton("수정", style = PillStyle.Secondary, fillMaxWidth)`; 누르면 시트 닫고 `onEdit(id)`)
- Modify: `app/src/main/java/com/jstensix/cardspending/ui/entry/ManualEntryScreen.kt` (제목: `editing`이면 `결제 수정`; `!state.loaded`인 동안 입력 영역 대신 빈 본문만 그려 `rememberSaveable`이 불러온 값으로 초기화되게 함 — 입력 칸을 담는 부분을 `if (state.loaded)`로 감싸고 그 안에서 `amount`/`merchant` 상태를 만든다)

**Interfaces:**
- Consumes: Task 2의 `ManualEntryViewModel(..., txId)`, `EntryState.editing`, `EntryState.loaded`.

- [ ] **Step 1: Build** `./gradlew :app:testDebugUnitTest :app:assembleDebug` — Expected: BUILD SUCCESSFUL, 실패 0.
- [ ] **Step 2: 기기 확인**(폰이 연결·잠금 해제돼 있으면): 직접 입력 결제 시트에 `수정`이 보이고 알림 결제에는 없음, 수정 화면 제목 `결제 수정`, 금액·가맹점이 미리 채워짐, 저장 후 목록 반영. 폰을 못 쓰면 그 사실을 보고에 적는다.
- [ ] **Step 3: Commit** — 위 3개 파일; `git commit -m "feat: 결제 시트에서 직접 입력 결제 수정"`
