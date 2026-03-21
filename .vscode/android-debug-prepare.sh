#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-common.sh"

serial="$(find_running_emulator)"

(
    cd "${repo_root}"
    ANDROID_SERIAL="${serial}" ./gradlew --console=plain installDebug
)

"${adb}" -s "${serial}" forward --remove "tcp:${debug_port}" >/dev/null 2>&1 || true
"${adb}" -s "${serial}" shell am clear-debug-app >/dev/null 2>&1 || true
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
    echo "Unable to determine app pid for ${app_id} on ${serial}." >&2
    exit 1
fi

"${adb}" -s "${serial}" forward "tcp:${debug_port}" "jdwp:${pid}"
write_state "${serial}" "${pid}"

echo "App installed on ${serial}, forwarded jdwp:${pid} to localhost:${debug_port}."
