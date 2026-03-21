#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-common.sh"

serial=""
if [[ -f "${state_file}" ]]; then
    # shellcheck disable=SC1090
    source "${state_file}"
    serial="${ANDROID_SERIAL:-}"
fi

if [[ -n "${serial}" ]]; then
    "${adb}" -s "${serial}" forward --remove "tcp:${debug_port}" >/dev/null 2>&1 || true
    "${adb}" -s "${serial}" shell am clear-debug-app >/dev/null 2>&1 || true
else
    "${adb}" forward --remove "tcp:${debug_port}" >/dev/null 2>&1 || true
fi

rm -f "${state_file}"
