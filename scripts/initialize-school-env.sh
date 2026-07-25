#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ -e .env || -L .env ]]; then
    echo "기존 .env를 보호하기 위해 초기화를 중단합니다." >&2
    echo "비밀번호를 잃어버렸다면 새 파일을 만들지 말고 기존 .env 또는 백업을 복구하세요." >&2
    exit 1
fi

[[ -f .env.school.example ]] || {
    echo "환경변수 템플릿이 없습니다: .env.school.example" >&2
    exit 1
}

for command_name in od awk tr chmod mv; do
    command -v "$command_name" >/dev/null 2>&1 || {
        echo "필수 명령을 찾을 수 없습니다: $command_name" >&2
        exit 1
    }
done

# /dev/urandom에서 256비트를 읽어 .env와 Compose에서 안전하게 사용할 수 있는
# 64자리 소문자 hex 문자열로 저장합니다. 생성된 값은 터미널에 출력하지 않습니다.
database_password="$(od -An -N32 -tx1 /dev/urandom | tr -d '[:space:]')"
if [[ ! "$database_password" =~ ^[0-9a-f]{64}$ ]]; then
    echo "안전한 DB 비밀번호 생성에 실패했습니다." >&2
    exit 1
fi

umask 077
temporary_env=".env.tmp.$$"
trap 'rm -f "$temporary_env"' EXIT

awk -v generated_password="$database_password" '
    BEGIN { replacements = 0 }
    /^POSTGRES_PASSWORD=/ {
        print "POSTGRES_PASSWORD=" generated_password
        replacements += 1
        next
    }
    { print }
    END {
        if (replacements != 1) {
            exit 42
        }
    }
' .env.school.example >"$temporary_env" || {
    echo "POSTGRES_PASSWORD 항목이 하나인 템플릿이 필요합니다." >&2
    exit 1
}

chmod 600 "$temporary_env"
mv "$temporary_env" .env
trap - EXIT

echo "학교 서버용 .env를 생성했습니다."
echo "- POSTGRES_PASSWORD: 256비트 난수로 설정됨(터미널에는 표시하지 않음)"
echo "- 다음 단계: .env의 프론트 주소와 SMTP 값을 실제 운영 값으로 수정"
echo "- 주의: .env를 Git에 추가하지 말고 암호화된 비밀 저장소에 별도로 백업"
