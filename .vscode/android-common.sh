#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
state_dir="${repo_root}/.vscode/.tmp"
state_file="${state_dir}/android-debug.env"
app_id="net.loeu.wallybudget"
launch_component="${app_id}/.MainActivity"
debug_port="8700"

sdk_dir=""
if [[ -f "${repo_root}/local.properties" ]]; then
    sdk_dir="$(
        sed -n 's/^sdk\.dir=//p' "${repo_root}/local.properties" |
            sed 's/\\:/:/g; s/\\\\/\\/g' |
            head -n 1
    )"
fi

if [[ -z "${sdk_dir}" && -n "${ANDROID_SDK_ROOT:-}" ]]; then
    sdk_dir="${ANDROID_SDK_ROOT}"
fi

if [[ -z "${sdk_dir}" && -n "${ANDROID_HOME:-}" ]]; then
    sdk_dir="${ANDROID_HOME}"
fi

if [[ -z "${sdk_dir}" && -d "${HOME}/Android/Sdk" ]]; then
    sdk_dir="${HOME}/Android/Sdk"
fi

if [[ -z "${sdk_dir}" ]]; then
    echo "Unable to locate Android SDK. Set sdk.dir in local.properties, ANDROID_SDK_ROOT, or use ${HOME}/Android/Sdk." >&2
    exit 1
fi

adb="${sdk_dir}/platform-tools/adb"
if [[ ! -x "${adb}" ]]; then
    echo "Unable to locate adb at ${adb}." >&2
    exit 1
fi

find_running_emulator() {
    mapfile -t emulator_serials < <(
        "${adb}" devices |
            tail -n +2 |
            sed '/^[[:space:]]*$/d' |
            awk '$2 == "device" && $1 ~ /^emulator-/ { print $1 }'
    )

    if [[ "${#emulator_serials[@]}" -eq 0 ]]; then
        echo "No running emulator detected. Start one emulator, then retry." >&2
        exit 1
    fi

    if [[ "${#emulator_serials[@]}" -ne 1 ]]; then
        echo "Multiple running emulators detected: ${emulator_serials[*]}." >&2
        echo "Leave one emulator connected and retry." >&2
        exit 1
    fi

    printf '%s\n' "${emulator_serials[0]}"
}

write_state() {
    mkdir -p "${state_dir}"
    cat > "${state_file}" <<EOF
ANDROID_SERIAL=$1
ANDROID_PID=$2
DEBUG_PORT=${debug_port}
APP_ID=${app_id}
EOF
}
