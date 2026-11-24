package com.jstensix.cardspending.parsing

internal const val LOTTE_SAMPLE =
    "세븐일레븐 판교LH테크노점\n11,000원 승인\n김수 텔로 SE(7*1*)\n일시불, 11/18 11:43\n누적금액 3,220,782원\n\n채널 추가하고 이 채널의 마케팅 메시지 등을 카카오톡으로 받기"

internal const val HANA_TITLE = "(결제) 9,900원"
internal const val HANA_BODY = "거궁찬판교점 / 신용(일시불,0*8*) / 11.18 17:29 / 누적이용금액 532,231원"

/** Samsung Messages wraps the sender name in Unicode isolates. */
internal const val HANA_SMS_TITLE = "⁨하나카드⁩"
internal const val HANA_SMS_BODY = "[Web발신]\n하나0*8*승인 김*수님 SK브로드밴드-자동납 26,950원 11/23 정상처리"
