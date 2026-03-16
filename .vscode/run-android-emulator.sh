#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"

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
        awk '$2 == "device" && $1 ~ /^emulator-/ { print $1 }'
)

if [[ "${#emulator_serials[@]}" -eq 0 ]]; then
    echo "No running emulator detected. Start one emulator, then rerun this launch profile." >&2
    exit 1
fi

if [[ "${#emulator_serials[@]}" -ne 1 ]]; then
    echo "Multiple running emulators detected: ${emulator_serials[*]}. Leave one emulator connected and retry." >&2
    exit 1
fi

serial="${emulator_serials[0]}"

(
    cd "${repo_root}"
    ANDROID_SERIAL="${serial}" ./gradlew --console=plain installDebug
)

"${adb}" -s "${serial}" shell am start -W -n net.loeu.wallybudget/.MainActivity
