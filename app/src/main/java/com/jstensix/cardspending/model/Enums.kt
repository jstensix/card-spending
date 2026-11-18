package com.jstensix.cardspending.model

enum class Issuer(val displayName: String) {
    LOTTE("롯데카드"),
    HANA("하나카드"),
}

enum class TxKind { APPROVAL, CANCEL }

enum class TxSource { NOTIFICATION, MANUAL }
