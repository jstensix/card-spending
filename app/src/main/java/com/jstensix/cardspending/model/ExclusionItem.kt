package com.jstensix.cardspending.model

/** Fixed list of "실적 미인정" items. Each card picks the ones its issuer excludes. */
enum class ExclusionItem(val label: String, val keywords: List<String>, val byInstallment: Boolean = false) {
    TAX("세금", listOf("국세", "지방세", "위택스", "홈택스", "세무서")),
    UTILITY("공과금", listOf("한국전력", "한전", "도시가스", "상수도", "수도요금")),
    APARTMENT_FEE("아파트관리비", listOf("관리비", "관리사무소")),
    GIFT_PREPAID("상품권·선불충전", listOf("상품권", "기프트카드", "선불")),
    INSURANCE("보험료", listOf("보험", "생명", "화재", "손해")),
    TUITION("등록금", listOf("등록금", "대학교")),
    INSTALLMENT("할부 결제", emptyList(), byInstallment = true),
    ;

    fun matches(merchant: String, installment: Boolean): Boolean =
        if (byInstallment) installment else keywords.any { it in merchant }
}
