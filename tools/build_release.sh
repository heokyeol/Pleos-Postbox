#!/bin/bash
# 서명한 release APK를 만들어 apk/소풍나온수송대대_PleosPostbox_release.apk로 복사한다.
#
# 키스토어는 저장소 밖(기본 ~/keystores/pleos-postbox.jks)에 둔다.
# 없으면 새로 만든다(keytool이 비밀번호와 이름 등을 묻는다).
# 비밀번호는 화면에 보이지 않게 입력받아 이 실행에서만 쓰고 어디에도 저장하지 않는다.
#
# 실행: bash tools/build_release.sh
set -euo pipefail

cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"

KEYSTORE="${PLEOS_KEYSTORE:-$HOME/keystores/pleos-postbox.jks}"
ALIAS="${PLEOS_KEY_ALIAS:-pleos-postbox}"
OUT="apk/소풍나온수송대대_PleosPostbox_release.apk"

case "$KEYSTORE" in
  "$PWD"/*) echo "키스토어를 저장소 안에 두지 마세요: $KEYSTORE" >&2; exit 1 ;;
esac

if [ ! -f "$KEYSTORE" ]; then
  echo "키스토어가 없어 새로 만듭니다: $KEYSTORE"
  mkdir -p "$(dirname "$KEYSTORE")"
  "$JAVA_HOME/bin/keytool" -genkeypair -v -storetype PKCS12 \
    -keystore "$KEYSTORE" -alias "$ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000
fi

read -r -s -p "키스토어 비밀번호: " PW; echo
export PLEOS_KEYSTORE="$KEYSTORE" PLEOS_KEY_ALIAS="$ALIAS"
export PLEOS_KEYSTORE_PASSWORD="$PW" PLEOS_KEY_PASSWORD="$PW"
unset PW

./gradlew :app:assembleRelease

mkdir -p apk
cp app/build/outputs/apk/release/app-release.apk "$OUT"

APKSIGNER="$(ls -d "$HOME"/Library/Android/sdk/build-tools/*/apksigner | sort -V | tail -1)"
"$APKSIGNER" verify --print-certs "$OUT" | head -3
echo "완료: $OUT"
