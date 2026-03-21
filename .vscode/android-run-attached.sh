#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-common.sh"

serial="$(find_running_emulator)"

(
    cd "${repo_root}"
    ANDROID_SERIAL="${serial}" ./gradlew --console=plain installDebug
)

"${adb}" -s "${serial}" shell am force-stop "${app_id}" >/dev/null 2>&1 || true
"${adb}" -s "${serial}" shell am start -W -n "${launch_component}" >/dev/null

pid=""
for _ in $(seq 1 20); do
    pid="$("${adb}" -s "${serial}" shell pidof -s "${app_id}" 2>/dev/null | tr -d '\r')"
    if [[ -n "${pid}" ]]; then
        break
    fi
    sleep 0.5
done

if [[ -z "${pid}" ]]; then
    echo "App launched on ${serial}, but pid lookup failed. Falling back to full logcat." >&2
    exec "${adb}" -s "${serial}" logcat
fi

echo "App running on ${serial}. Streaming logcat for ${app_id} (pid ${pid}). Press Ctrl+C to stop."
exec "${adb}" -s "${serial}" logcat --pid="${pid}"
