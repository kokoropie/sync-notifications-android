#!/usr/bin/env bash
# Tạo android/app/release.keystore + android/keystore.properties với mật khẩu ngẫu nhiên.
# Không ghi đè nếu đã tồn tại. Cả hai file đều bị git bỏ qua: HÃY SAO LƯU Ở NƠI AN TOÀN.
set -euo pipefail
cd "$(dirname "$0")/../android"

KS=app/release.keystore
PROPS=keystore.properties
[[ -e "$KS" ]] && { echo "Đã có $KS, không ghi đè."; exit 1; }
[[ -e "$PROPS" ]] && { echo "Đã có $PROPS, không ghi đè."; exit 1; }

JAVA_HOME="${JAVA_HOME_17:-$(/usr/libexec/java_home -v 17 2>/dev/null || echo "${JAVA_HOME:-}")}"
KEYTOOL="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"
PW=$(openssl rand -hex 20)

"$KEYTOOL" -genkeypair -storetype PKCS12 -keystore "$KS" -alias notify -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "${DNAME:-CN=Kaga Akatsuki, O=kokoropie, C=VN}" -storepass "$PW" -keypass "$PW"

umask 077
printf 'storeFile=%s\nstorePassword=%s\nkeyAlias=notify\nkeyPassword=%s\n' "$KS" "$PW" "$PW" > "$PROPS"
echo "Đã tạo android/$KS và android/$PROPS (mật khẩu nằm trong file đó). Sao lưu cả hai!"
