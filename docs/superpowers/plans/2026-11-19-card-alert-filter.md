# 알림함 판단을 등록 카드 번호로 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 파서가 이해 못 한 알림은 등록 카드의 마스킹 번호/끝 4자리가 있을 때만 알림함에 넣고, 기존 쓸데없는 항목은 재검사로 지운다.

**Architecture:** `CardNotificationParser.parse`가 `knownPackages` 대신 등록 카드 목록을 받는다. `Ingestor`는 저장소의 카드 목록을 넘기고, `reparsePending`은 무시 대상이 된 항목을 삭제한다. `PARSER_VERSION` 1→2.

**Tech Stack:** Kotlin, Room, Robolectric 단위 테스트.

**Spec:** `docs/superpowers/specs/2026-11-19-card-alert-filter-design.md`

## Global Constraints

- 롯데/하나 파서 동작은 그대로(인식된 결제는 카드 등록 여부와 무관하게 지금처럼 처리).
- 후보 조건: 텍스트(제목+본문)에 `maskOf(card.last4)` 문자열 또는 `(?<!\d)<last4>(?!\d)`가 있음. 메신저/대화형은 추가로 제목에 '롯데카드'/'하나카드'.
- 커밋은 명시 경로만, 메시지 끝 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

- 쇼핑 앱 본문의 '롯데카드' 언급, `(광고)[하나카드]`, 결제예정금액 안내 → 무시. Task 1 `issuerNameOnly_ignored`, `adWithoutCardNumber_ignored`.
- 해외 결제 푸시(번호 있음, 파서 미인식) → 여전히 후보. Task 1 `foreignCurrencyCardPush_candidate`.
- 다른 사람 카드 번호(미등록 마스크) → 무시. Task 1 `unregisteredMask_ignored`.
- 금액 속 숫자(`17,419원`, `174190`)가 끝 4자리로 오인되지 않음. Task 1 `last4InsideNumber_notMatched`.
- 앱 시작 재검사가 쓸데없는 항목만 지우고 번호 있는 후보는 남김. Task 2 `reparsePending_deletesNoLongerCandidates`.

---

### Task 1: 판단 규칙

**Files:** Modify `parsing/CardNotificationParser.kt`; Test `parsing/CardNotificationParserTest.kt`

**Interfaces:**
- Produces: `CardNotificationParser.parse(message: NotificationMessage, cards: List<Card>): ParseOutcome`; `PARSER_VERSION = 2`

- [ ] **Step 1: Tests** (등록 카드: 롯데 `7419`(→`7*1*`), 하나 `0381`(→`0*8*`))
  - 기존 테스트의 두 번째 인자를 카드 목록으로 바꾼다. `knownPackagePaymentText_*`는 삭제(규칙 제거).
  - `unknownIssuerFormat_candidate`: 카톡 제목 `하나카드`, 본문 `[하나카드] 홍*동님 9,900원 승인 0*8*` → Candidate
  - `issuerTitleWithoutNumber_ignored`: 카톡 제목 `롯데카드`, 본문 `이번 달 혜택 안내` → Ignored
  - `issuerNameOnly_ignored`: 앱 `com.pickle.client`, 본문 `롯데카드 있으시면 하이마트(859,140원)가 더 싸요` → Ignored
  - `adWithoutCardNumber_ignored`: 앱 `com.hanaskcard.paycla`, 제목 `(광고)[하나카드](1800-1111)` → Ignored
  - `unregisteredMask_ignored`: 본문 `카드(5*2*) 12,000원` → Ignored
  - `maskAndAmountInUnknownLayout_candidate`: 본문 `카드(0*8*) 12,000원` → Candidate(기존 유지)
  - `foreignCurrencyCardPush_candidate`: 기존 유지
  - `plainLast4_candidate`: 본문 `카드 끝자리 7419 승인 12,000원` → Candidate
  - `last4InsideNumber_notMatched`: 본문 `17,419원` 과 `174190` 각각 → Ignored
  - `noCards_unknownTextIgnored`: 카드 목록 비었을 때 `카드(0*8*) 12,000원` → Ignored
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*CardNotificationParserTest"` → FAIL(시그니처 불일치)
- [ ] **Step 3: Implement** 규칙(Global Constraints), `maskOf` 재사용.
- [ ] **Step 4: Run** 같은 명령 → PASS
- [ ] **Step 5: Commit** `feat: 알림함 후보를 등록 카드 번호가 있는 알림으로 제한`

### Task 2: 수신·재검사 연결

**Files:** Modify `ingest/Ingestor.kt`, `data/SpendingRepository.kt`(쓰지 않게 된 `knownSourcePackages` 제거, `TransactionDao.sourcePackages` 제거); Test `ingest/IngestorTest.kt`

**Interfaces:**
- Consumes: Task 1 `parse(message, cards)`
- Ingestor는 `repo.observeCards().first()`를 넘긴다. `reparsePending()`: Parsed → 저장+숨김(기존), Ignored → `repo.deleteUnparsed(id)`, Candidate → 버전 갱신.

- [ ] **Step 1: Tests** — `setUp`에 롯데 `7419`, 하나 `0381` 카드 등록.
  - `unknownCardFormat_storedOnceAsUnparsed`: 본문에 `0*8*` 추가해 기존 기대 유지
  - `issuerMentionInOtherApp_notStored`: 앱 `com.pickle.client`, 본문 `롯데카드 할인` → IGNORED, 알림함 비어 있음
  - `reparsePending_deletesNoLongerCandidates`: 버전 0 항목 3개 — LOTTE_SAMPLE(→결제 저장, 숨김), `롯데카드 알 수 없는 문구 7*1*`(→남음, 버전 2), `롯데카드 이번 달 혜택`(→삭제). `repo.snapshot()`의 unparsed에 세 번째가 없어야 함.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*IngestorTest"` → FAIL
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest :app:assembleDebug` → PASS
- [ ] **Step 5: Commit** `feat: 기존 알림함 항목을 새 규칙으로 재검사해 정리`
