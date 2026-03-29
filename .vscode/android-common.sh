#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
state_dir="${repo_root}/.vscode/.tmp"
state_file="${state_dir}/android-debug.env"
app_id="net.loeu.wallybudget"
launch_component="${app_id}/.MainActivity"
debug_port="8700"

expand_home_path() {
    local path="$1"

    if [[ "${path}" == "~" ]]; then
        printf '%s\n' "${HOME}"
    elif [[ "${path}" == "~/"* ]]; then
        printf '%s\n' "${HOME}${path:1}"
    else
        printf '%s\n' "${path}"
    fi
}

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

if [[ -n "${sdk_dir}" ]]; then
    sdk_dir="$(expand_home_path "${sdk_dir}")"
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

list_running_emulator_lines() {
    "${adb}" devices -l |
        tail -n +2 |
        sed '/^[[:space:]]*$/d' |
        awk '$2 == "device" && $1 ~ /^emulator-/ { print }'
}

find_running_emulator() {
    local -a emulator_lines
    local line=""
    local serial=""

    mapfile -t emulator_lines < <(list_running_emulator_lines)

    if [[ "${#emulator_lines[@]}" -eq 0 ]]; then
        echo "No running emulator detected. Start one emulator, then retry." >&2
        exit 1
    fi

    if [[ -n "${ANDROID_SERIAL:-}" ]]; then
        for line in "${emulator_lines[@]}"; do
            serial="${line%%[[:space:]]*}"
            if [[ "${serial}" == "${ANDROID_SERIAL}" ]]; then
                printf '%s\n' "${serial}"
                return
            fi
        done

        echo "ANDROID_SERIAL=${ANDROID_SERIAL} does not match any running emulator." >&2
        echo "Detected emulators:" >&2
        printf ' - %s\n' "${emulator_lines[@]}" >&2
        exit 1
    fi

    if [[ "${#emulator_lines[@]}" -eq 1 ]]; then
        printf '%s\n' "${emulator_lines[0]%%[[:space:]]*}"
        return
    fi

    if [[ ! -t 0 ]]; then
        echo "Multiple running emulators detected:" >&2
        printf ' - %s\n' "${emulator_lines[@]}" >&2
        echo "Set ANDROID_SERIAL to one of the serials above, then retry." >&2
        exit 1
    fi

    echo "Multiple running emulators detected:"
    local index=1
    local selection=""

    for line in "${emulator_lines[@]}"; do
        printf '  %d) %s\n' "${index}" "${line}"
        index=$((index + 1))
    done

    while true; do
        read -r -p "Select emulator [1-${#emulator_lines[@]} or serial]: " selection

        if [[ "${selection}" =~ ^[0-9]+$ ]] &&
            [[ "${selection}" -ge 1 ]] &&
            [[ "${selection}" -le "${#emulator_lines[@]}" ]]; then
            serial="${emulator_lines[$((selection - 1))]%%[[:space:]]*}"
            printf '%s\n' "${serial}"
            return
        fi

        for line in "${emulator_lines[@]}"; do
            serial="${line%%[[:space:]]*}"
            if [[ "${selection}" == "${serial}" ]]; then
                printf '%s\n' "${serial}"
                return
            fi
        done

        echo "Invalid selection: ${selection}" >&2
    done
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
