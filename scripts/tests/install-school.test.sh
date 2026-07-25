#!/usr/bin/env bash
set -euo pipefail

SOURCE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TARGET_SCRIPT="${SOURCE_ROOT}/install-school.sh"
FIXTURE_ROOT="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_ROOT"' EXIT

[[ -x "$TARGET_SCRIPT" ]] || {
    echo "원클릭 설치 스크립트를 실행할 수 없습니다: $TARGET_SCRIPT" >&2
    exit 1
}

mkdir -p "$FIXTURE_ROOT/scripts"
cp "$SOURCE_ROOT/.env.school.example" "$FIXTURE_ROOT/.env.school.example"
cp "$SOURCE_ROOT/scripts/initialize-school-env.sh" \
    "$FIXTURE_ROOT/scripts/initialize-school-env.sh"
cp "$TARGET_SCRIPT" "$FIXTURE_ROOT/install-school.sh"

smtp_password="smtp pa\$#'word"
first_output="$(
    cd "$FIXTURE_ROOT"
    INSTALL_ALLOWED_ORIGINS="https://timetable.daejin.ac.kr,http://localhost:5173" \
    INSTALL_SMTP_HOST="smtp.daejin.ac.kr" \
    INSTALL_SMTP_USERNAME="timetable@daejin.ac.kr" \
    INSTALL_SMTP_PASSWORD="$smtp_password" \
    INSTALL_OTP_FROM="timetable@daejin.ac.kr" \
        ./install-school.sh --configure-only
)"

database_password="$(sed -n 's/^POSTGRES_PASSWORD=//p' "$FIXTURE_ROOT/.env")"
[[ "$database_password" =~ ^[0-9a-f]{64}$ ]]
[[ "$first_output" != *"$database_password"* ]]
[[ "$first_output" != *"$smtp_password"* ]]
grep -q '^ALLOWED_ORIGINS=https://timetable.daejin.ac.kr,http://localhost:5173$' \
    "$FIXTURE_ROOT/.env"
grep -q '^SMTP_HOST=smtp.daejin.ac.kr$' "$FIXTURE_ROOT/.env"
grep -q '^SMTP_USERNAME=timetable@daejin.ac.kr$' "$FIXTURE_ROOT/.env"
grep -q "^SMTP_PASSWORD='smtp pa\\\$#\\\\'word'$" "$FIXTURE_ROOT/.env"
grep -q '^OTP_FROM=timetable@daejin.ac.kr$' "$FIXTURE_ROOT/.env"
[[ "$(stat -c '%a' "$FIXTURE_ROOT/.env")" == "600" ]]

before_checksum="$(sha256sum "$FIXTURE_ROOT/.env" | cut -d' ' -f1)"
(
    cd "$FIXTURE_ROOT"
    INSTALL_ALLOWED_ORIGINS="https://should-not-overwrite.example.com" \
    INSTALL_SMTP_HOST="should-not-overwrite.example.com" \
    INSTALL_SMTP_USERNAME="other@example.com" \
    INSTALL_SMTP_PASSWORD="other-password" \
    INSTALL_OTP_FROM="other@example.com" \
        ./install-school.sh --configure-only >/dev/null
)
after_checksum="$(sha256sum "$FIXTURE_ROOT/.env" | cut -d' ' -f1)"
[[ "$before_checksum" == "$after_checksum" ]]

printf 'fixture bundle\n' >"$FIXTURE_ROOT/source-bundle.tar.gz"
cat >"$FIXTURE_ROOT/scripts/bootstrap-school.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
[[ -f data/database/academic-data-bundle.tar.gz ]]
printf 'bootstrap-called\n' >bootstrap.marker
EOF
chmod 755 "$FIXTURE_ROOT/scripts/bootstrap-school.sh"
(
    cd "$FIXTURE_ROOT"
    INSTALL_DATA_BUNDLE="$FIXTURE_ROOT/source-bundle.tar.gz" \
        ./install-school.sh >/dev/null
)
cmp "$FIXTURE_ROOT/source-bundle.tar.gz" \
    "$FIXTURE_ROOT/data/database/academic-data-bundle.tar.gz"
grep -q '^bootstrap-called$' "$FIXTURE_ROOT/bootstrap.marker"

echo "install-school: PASS"
