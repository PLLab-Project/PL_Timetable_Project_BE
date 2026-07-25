#!/usr/bin/env bash
set -euo pipefail
umask 077

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

configure_only=false
case "${1:-}" in
    "")
        ;;
    --configure-only)
        configure_only=true
        ;;
    -h|--help)
        cat <<'EOF'
사용법: ./install-school.sh [--configure-only]

Docker Desktop과 WSL2를 준비한 뒤 실행하는 학교 서버 원클릭 설치 진입점입니다.
필수 운영값을 최초 한 번만 입력받고 DB 비밀번호를 자동 생성한 다음 백엔드를 시작합니다.

--configure-only  .env 설정까지만 수행하고 Docker·DB·API는 시작하지 않습니다.
EOF
        exit 0
        ;;
    *)
        echo "지원하지 않는 인자입니다: $1" >&2
        exit 1
        ;;
esac

if [[ ! -f .env ]]; then
    ./scripts/initialize-school-env.sh
else
    chmod 600 .env
    echo "기존 .env를 덮어쓰지 않고 사용합니다."
fi

env_value() {
    local key="$1"
    sed -n "s/^${key}=//p" .env | tail -n 1
}

replace_env_line() {
    local key="$1"
    local rendered_line="$2"
    local temporary_env=".env.install.$$"
    local replacements=0
    local line

    : >"$temporary_env"
    while IFS= read -r line || [[ -n "$line" ]]; do
        if [[ "$line" == "${key}="* ]]; then
            printf '%s\n' "$rendered_line" >>"$temporary_env"
            replacements=$((replacements + 1))
        else
            printf '%s\n' "$line" >>"$temporary_env"
        fi
    done <.env

    if [[ "$replacements" -ne 1 ]]; then
        rm -f "$temporary_env"
        echo ".env에 ${key} 항목이 정확히 하나 있어야 합니다." >&2
        exit 1
    fi

    chmod 600 "$temporary_env"
    mv "$temporary_env" .env
}

read_install_value() {
    local -n result_reference="$1"
    local environment_variable="$2"
    local prompt="$3"
    local secret="${4:-false}"
    local input_value="${!environment_variable:-}"

    if [[ -z "$input_value" ]]; then
        if [[ ! -t 0 ]]; then
            echo "비대화형 실행에는 ${environment_variable} 환경변수가 필요합니다." >&2
            exit 1
        fi
        if [[ "$secret" == true ]]; then
            read -r -s -p "${prompt}: " input_value
            echo
        else
            read -r -p "${prompt}: " input_value
        fi
    fi

    result_reference="$input_value"
}

configure_raw_value() {
    local key="$1"
    local environment_variable="$2"
    local prompt="$3"
    local validation_pattern="$4"
    local current
    local value

    current="$(env_value "$key")"
    if [[ -n "$current" && "$current" != *example* ]]; then
        return
    fi

    read_install_value value "$environment_variable" "$prompt"
    if [[ ! "$value" =~ $validation_pattern ]]; then
        echo "${key} 값의 형식이 올바르지 않습니다." >&2
        exit 1
    fi
    replace_env_line "$key" "${key}=${value}"
}

configure_raw_value \
    ALLOWED_ORIGINS \
    INSTALL_ALLOWED_ORIGINS \
    "허용할 프론트 Origin(쉼표로 구분)" \
    '^https?://[^[:space:],]+(,https?://[^[:space:],]+)*$'
configure_raw_value \
    SMTP_HOST \
    INSTALL_SMTP_HOST \
    "OTP 발송 SMTP 호스트" \
    '^[A-Za-z0-9.-]+$'
configure_raw_value \
    SMTP_USERNAME \
    INSTALL_SMTP_USERNAME \
    "OTP 발송 SMTP 사용자 이름" \
    '^[^[:space:]]+$'

smtp_password="$(env_value SMTP_PASSWORD)"
if [[ -z "$smtp_password" ]]; then
    read_install_value \
        smtp_password \
        INSTALL_SMTP_PASSWORD \
        "OTP 발송 SMTP 비밀번호" \
        true
    if [[ "${#smtp_password}" -lt 8 || "$smtp_password" == *$'\n'* ]]; then
        echo "SMTP_PASSWORD는 8자 이상이어야 합니다." >&2
        exit 1
    fi
    escaped_password="$(printf '%s' "$smtp_password" | sed "s/'/\\\\'/g")"
    replace_env_line SMTP_PASSWORD "SMTP_PASSWORD='${escaped_password}'"
    unset smtp_password escaped_password
fi

configure_raw_value \
    OTP_FROM \
    INSTALL_OTP_FROM \
    "OTP 발신 주소" \
    '^[^[:space:]@]+@[^[:space:]@]+$'

if [[ "$(env_value SPRING_PROFILES_ACTIVE)" != "prod" ]]; then
    echo "학교 서버 설치는 SPRING_PROFILES_ACTIVE=prod 설정이 필요합니다." >&2
    exit 1
fi

echo "학교 서버 운영 환경 설정을 확인했습니다."

if [[ "$configure_only" == true ]]; then
    echo "--configure-only: Docker·DB·API 시작은 생략합니다."
    exit 0
fi

bundle_destination="data/database/academic-data-bundle.tar.gz"
if [[ ! -f "$bundle_destination" ]]; then
    bundle_source="${INSTALL_DATA_BUNDLE:-}"

    if [[ -z "$bundle_source" ]]; then
        shopt -s nullglob
        candidates=(/mnt/c/Users/*/Downloads/academic-data-bundle.tar.gz)
        shopt -u nullglob
        if [[ "${#candidates[@]}" -eq 1 ]]; then
            bundle_source="${candidates[0]}"
            echo "Windows 다운로드 폴더에서 학사 데이터 번들을 찾았습니다."
        elif [[ -t 0 ]]; then
            read -r -p "academic-data-bundle.tar.gz 경로: " bundle_source
        else
            echo "INSTALL_DATA_BUNDLE로 학사 데이터 번들 경로를 지정하세요." >&2
            exit 1
        fi
    fi

    [[ -f "$bundle_source" ]] || {
        echo "학사 데이터 번들을 찾을 수 없습니다: $bundle_source" >&2
        exit 1
    }
    mkdir -p "$(dirname "$bundle_destination")"
    cp "$bundle_source" "$bundle_destination"
    chmod 600 "$bundle_destination"
    echo "학사 데이터 번들을 data/database/에 배치했습니다."
fi

echo "DB 마이그레이션·데이터 적재·API 시작을 진행합니다."
exec ./scripts/bootstrap-school.sh
