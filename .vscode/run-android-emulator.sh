#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/android-common.sh"

mode="${1:-launch}"

if [[ "${mode}" != "launch" && "${mode}" != "deploy" ]]; then
    echo "Usage: ${0} [launch|deploy]" >&2
    exit 1
fi
serial="$(find_running_emulator)"

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

"${adb}" -s "${serial}" shell am start -W -S -n "${launch_component}"
