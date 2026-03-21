#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
app_id="net.loeu.wallybudget"
activity_name="${app_id}/.MainActivity"
mode="${1:-launch}"

if [[ "${mode}" != "launch" && "${mode}" != "deploy" ]]; then
    echo "Usage: ${0} [launch|deploy]" >&2
    exit 1
fi

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

if [[ -z "${sdk_dir}" ]]; then
    echo "Unable to locate Android SDK. Set sdk.dir in local.properties or ANDROID_SDK_ROOT." >&2
    exit 1
fi

adb="${sdk_dir}/platform-tools/adb"
if [[ ! -x "${adb}" ]]; then
    echo "Unable to locate adb at ${adb}." >&2
    exit 1
fi

mapfile -t emulator_serials < <(
    "${adb}" devices |
        tail -n +2 |
        sed '/^[[:space:]]*$/d' |
        awk '$2 == "device" { print $1 }'
)

if [[ "${#emulator_serials[@]}" -eq 0 ]]; then
    echo "No connected Android device or emulator detected. Connect one target, then rerun this launch profile." >&2
    exit 1
fi

if [[ "${#emulator_serials[@]}" -ne 1 ]]; then
    echo "Multiple Android targets detected: ${emulator_serials[*]}. Leave one target connected and retry." >&2
    exit 1
fi

serial="${emulator_serials[0]}"

if [[ "${mode}" == "deploy" ]]; then
    (
        cd "${repo_root}"
        ANDROID_SERIAL="${serial}" ./gradlew --console=plain :app:assembleDebug
    )

    apk_path="${repo_root}/app/build/outputs/apk/debug/app-debug.apk"
    if [[ ! -f "${apk_path}" ]]; then
        echo "Debug APK not found at ${apk_path}. Build may have failed." >&2
        exit 1
    fi

    "${adb}" -s "${serial}" install -r "${apk_path}" >/dev/null
elif ! "${adb}" -s "${serial}" shell pm path "${app_id}" >/dev/null 2>&1; then
    echo "The debug app is not installed on ${serial}. Run this profile with 'deploy' once first." >&2
    exit 1
fi

"${adb}" -s "${serial}" shell am start -W -S -n "${activity_name}"
