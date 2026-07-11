#!/usr/bin/env bash
# Builds the ha-dashclock project inside a Podman container, so you don't need a
# local JDK/Android SDK/Gradle install on the host at all.
#
# Usage:
#   bash container/build.sh                 # ./gradlew-equivalent: assembleDebug
#   bash container/build.sh assembleRelease  # any Gradle task
#
# Output APKs land under app/build/outputs/apk/**, on the host, because the
# project directory is bind-mounted into the container (not copied).
set -euo pipefail

IMAGE_NAME="ha-dashclock-build"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TASK="${1:-assembleDebug}"

podman build -t "${IMAGE_NAME}" "${PROJECT_DIR}/container"

podman run --rm \
    -v "${PROJECT_DIR}:/workspace:Z" \
    -v ha-dashclock-gradle-cache:/root/.gradle \
    -v ha-dashclock-android-home:/root/.android \
    -w /workspace \
    "${IMAGE_NAME}" \
    gradle --no-daemon "${TASK}"

echo ""
echo "Done. Look under app/build/outputs/apk/ for the APK."
