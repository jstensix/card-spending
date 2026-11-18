#!/usr/bin/env bash
# Generates the app's vector icons (style of the user's ChatGPT icon sheet: blue→indigo gradient fills).
# Usage (repo root): bash tools/gen-icons.sh app/src/main/res/drawable
set -e
OUT="$1"   # res/drawable dir
mkdir -p "$OUT"

# rr l t r b radius → SVG path for a rounded rectangle
rr() {
  awk -v l="$1" -v t="$2" -v r="$3" -v b="$4" -v k="$5" 'BEGIN {
    printf "M%g,%g H%g A%g,%g 0 0 1 %g,%g V%g A%g,%g 0 0 1 %g,%g H%g A%g,%g 0 0 1 %g,%g V%g A%g,%g 0 0 1 %g,%g Z",
      l+k, t, r-k, k, k, r, t+k, b-k, k, k, r-k, b, l+k, k, k, l, b-k, t+k, k, k, l+k, t
  }'
}
# circle cx cy r
circ() {
  awk -v x="$1" -v y="$2" -v r="$3" 'BEGIN { printf "M%g,%g a%g,%g 0 1 0 %g,0 a%g,%g 0 1 0 %g,0 Z", x-r, y, r, r, 2*r, r, r, -2*r }'
}

BLUE="#FF3F8CFF"; INDIGO="#FF533AFD"
GREY_A="#FFC3CAD6"; GREY_B="#FF97A2B4"

# grad x1 y1 x2 y2 c1 c2 → fillColor gradient element
grad() {
  cat <<EOF
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="$1" android:startY="$2" android:endX="$3" android:endY="$4">
                <item android:offset="0" android:color="$5" />
                <item android:offset="1" android:color="$6" />
            </gradient>
        </aapt:attr>
EOF
}

head24() {
  cat <<EOF
<?xml version="1.0" encoding="utf-8"?>
<!-- $1 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
EOF
}

# gpath "pathdata" c1 c2 [x1 y1 x2 y2]
gpath() {
  local d="$1" c1="$2" c2="$3" x1="${4:-3}" y1="${5:-2}" x2="${6:-21}" y2="${7:-22}"
  echo "    <path android:pathData=\"$d\">"
  grad "$x1" "$y1" "$x2" "$y2" "$c1" "$c2"
  echo "    </path>"
}
fpath() { echo "    <path android:fillColor=\"$2\" android:pathData=\"$1\" />"; }

BELL="M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z"
GEAR="M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z"
LOCK="M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z"
CHECK_CIRCLE="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z"

# ---- 홈 tab: the app tile (card + bars), on/off ----
home() { # c1 c2 file
  {
    head24 "홈 탭: 앱 아이콘 축소판 (사용자 아이콘 시안)"
    gpath "$(rr 2 2 22 22 5.5)" "$1" "$2"
    echo '    <group android:rotation="-12" android:pivotX="9.6" android:pivotY="9.4">'
    fpath "$(rr 5 6.6 14.2 12.2 1.3)" "#FFFFFFFF"
    fpath "M5,8.1 H14.2 V9.3 H5 Z" "#FFA9C7FF"
    fpath "$(rr 6.2 10.3 8.8 11.1 0.4)" "#FF8DB2FF"
    echo '    </group>'
    fpath "$(rr 13.6 14.4 14.9 18.2 0.65)" "#FFFFFFFF"
    fpath "$(rr 15.8 12.6 17.1 18.2 0.65)" "#FFFFFFFF"
    fpath "$(rr 18 10.6 19.3 18.2 0.65)" "#FFFFFFFF"
    echo '</vector>'
  } > "$OUT/$3"
}
home "$BLUE" "$INDIGO" ic_tab_home_on.xml
home "$GREY_A" "$GREY_B" ic_tab_home_off.xml

# ---- 카드 tab ----
card() { # c1 c2 file
  {
    head24 "카드 탭"
    gpath "$(rr 2 5 22 19 3)" "$1" "$2"
    fpath "M2,8.6 H22 V11 H2 Z" "#55FFFFFF"
    fpath "$(rr 4.6 14.2 9.4 15.8 0.8)" "#99FFFFFF"
    fpath "$(rr 15.2 13.8 19.4 16.2 1.2)" "#99FFFFFF"
    echo '</vector>'
  } > "$OUT/$3"
}
card "$BLUE" "$INDIGO" ic_tab_card_on.xml
card "$GREY_A" "$GREY_B" ic_tab_card_off.xml

# ---- 알림 / 설정 tabs ----
single() { # path c1 c2 file comment
  { head24 "$5"; gpath "$1" "$2" "$3"; echo '</vector>'; } > "$OUT/$4"
}
single "$BELL" "$BLUE" "$INDIGO" ic_tab_bell_on.xml "알림 탭 (Material notifications 모양, Apache 2.0)"
single "$BELL" "$GREY_A" "$GREY_B" ic_tab_bell_off.xml "알림 탭 (Material notifications 모양, Apache 2.0)"
single "$GEAR" "$BLUE" "$INDIGO" ic_tab_settings_on.xml "설정 탭 (Material settings 모양, Apache 2.0)"
single "$GEAR" "$GREY_A" "$GREY_B" ic_tab_settings_off.xml "설정 탭 (Material settings 모양, Apache 2.0)"
single "$LOCK" "$BLUE" "$INDIGO" ic_lock.xml "보안 (Material lock 모양, Apache 2.0)"
single "$CHECK_CIRCLE" "$BLUE" "$INDIGO" ic_check_circle.xml "완료 (Material check_circle 모양, Apache 2.0)"

# ---- 기간 설정: calendar with check ----
{
  head24 "기간 설정"
  gpath "$(rr 3 4.5 21 21 3)" "$BLUE" "$INDIGO"
  fpath "$(rr 5 9.5 19 19 1.6)" "#FFFFFFFF"
  gpath "$(rr 7 2.4 8.8 6.6 0.9)" "$INDIGO" "$INDIGO"
  gpath "$(rr 15.2 2.4 17 6.6 0.9)" "$INDIGO" "$INDIGO"
  echo '    <path android:pathData="M8.6,14.3 L11,16.6 L15.6,12" android:strokeColor="#FF533AFD" android:strokeWidth="1.8" android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000" />'
  echo '</vector>'
} > "$OUT/ic_calendar.xml"

# ---- 카드 추가: card + plus badge ----
{
  head24 "카드 추가"
  gpath "$(rr 1.5 4.5 18.5 15.5 2.4)" "$BLUE" "$INDIGO"
  fpath "M1.5,7.2 H18.5 V9.2 H1.5 Z" "#55FFFFFF"
  fpath "$(rr 4 12 7.6 13.3 0.65)" "#99FFFFFF"
  fpath "$(circ 17.5 17.5 5.6)" "#FFFFFFFF"
  gpath "$(circ 17.5 17.5 4.6)" "$BLUE" "$INDIGO" 13 13 22 22
  fpath "$(rr 16.8 15 18.2 20 0.6)" "#FFFFFFFF"
  fpath "$(rr 15 16.8 20 18.2 0.6)" "#FFFFFFFF"
  echo '</vector>'
} > "$OUT/ic_card_add.xml"

# ---- 주의: orange circle with ! ----
{
  head24 "주의"
  gpath "$(circ 12 12 10)" "#FFFFB13D" "#FFFF5A2D" 4 3 20 21
  fpath "$(rr 10.9 6.2 13.1 13.8 1.1)" "#FFFFFFFF"
  fpath "$(circ 12 17.2 1.35)" "#FFFFFFFF"
  echo '</vector>'
} > "$OUT/ic_warning.xml"

# ---- launcher (108dp adaptive icon) ----
head108() {
  cat <<EOF
<?xml version="1.0" encoding="utf-8"?>
<!-- $1 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
EOF
}
{
  head108 "런처 아이콘 배경: 파랑→인디고 그라데이션"
  echo '    <path android:pathData="M0,0 H108 V108 H0 Z">'
  grad 0 0 108 108 "$BLUE" "$INDIGO"
  echo '    </path>'
  echo '</vector>'
} > "$OUT/ic_launcher_bg.xml"

launcher_fg() { # file mono(0/1)
  local W="#FFFFFFFF" S="#FFA9C7FF" M="#FF8DB2FF" R="#E6EA2261" O="#E6FF9A3D" B="#F2FFFFFF"
  if [ "$2" = 1 ]; then S="$W"; M="$W"; R="$W"; O="$W"; B="$W"; fi
  {
    head108 "런처 아이콘 전경: 카드 + 막대 그래프 (안전 영역 반지름 33 안)"
    echo '    <group android:rotation="-12" android:pivotX="44" android:pivotY="44.5">'
    fpath "$(rr 28 34 60 55 4.5)" "$W"
    [ "$2" = 1 ] || fpath "M28,38.5 H60 V42.5 H28 Z" "$S"
    [ "$2" = 1 ] || fpath "$(rr 32 47.5 41 50.5 1.4)" "$M"
    [ "$2" = 1 ] || fpath "$(circ 50.6 49 2.8)" "$R"
    [ "$2" = 1 ] || fpath "$(circ 54 49 2.8)" "$O"
    echo '    </group>'
    fpath "$(rr 55 62 60 76 2.5)" "$B"
    fpath "$(rr 63 55 68 76 2.5)" "$B"
    fpath "$(rr 71 47 76 76 2.5)" "$B"
    echo '</vector>'
  } > "$OUT/$1"
}
launcher_fg ic_launcher_fg.xml 0
launcher_fg ic_launcher_mono.xml 1
ls "$OUT" | grep -E "ic_(tab|launcher_(bg|fg|mono)|lock|check|calendar|card_add|warning)"
